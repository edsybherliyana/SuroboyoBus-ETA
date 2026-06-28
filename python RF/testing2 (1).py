# =======================================================================
# FILE 3: INFERENCE REALTIME v4 — HYBRID MODEL (MULTI-BUS)
# Perubahan dari versi sebelumnya:
#  - Load model_eta_v2.pkl + mean_ratio_per_npre2.pkl
#  - predict_eta() menggunakan strategi hybrid:
#      n ≤ 4  → RF ratio model
#      5-8    → 50% RF + 50% mean rasio per n
#      n > 8  → cum_hist × mean_ratio[n] (tanpa RF)
#  - Fitur: is_rush_hour (bukan hour) — konsisten dengan training
#  - Pertahankan EWMA, rolling stop_intensity, fallback berhenti
#  - [UPDATE] Menghitung ETA untuk bus_1 DAN bus_2, keduanya dikirim ke Firebase
# =======================================================================

import firebase_admin
from firebase_admin import credentials, db
import joblib
import pandas as pd
import numpy as np
import time, csv
from collections import deque #antrian FIFO ukuran tetap untuk rolling buffer stop intensity
from datetime import datetime #mengambil jam saat ini dan membuat time stamp log

# ===============================
# INIT FIREBASE
# ===============================
cred = credentials.Certificate("eb-tracking-1c96a-firebase-adminsdk-fbsvc-a2610475e4.json")
firebase_admin.initialize_app(cred, {
    "databaseURL": "https://eb-tracking-1c96a-default-rtdb.firebaseio.com/"
})

# ===============================
# LOAD MODEL & PENDUKUNG (pemanggilan pkl pickle dan csv Comma-Separated Values)
# ===============================
model            = joblib.load("model_eta_v2.pkl")
mean_ratio_per_n = joblib.load("mean_ratio_per_npre2.pkl")   # fallback untuk n besar
seg_avg_eta      = joblib.load("seg_avg_etapre2.pkl")

seg_df          = pd.read_csv("segment_profilepre2.csv").set_index("segment_id_str")
segment_profile = seg_df.to_dict(orient="index")

# ===============================
# DAFTAR BUS YANG DIPROSES
# ===============================
BUS_LIST = ["bus_1", "bus_2"]

# ===============================
# THRESHOLD STRATEGI
# ===============================
THRESHOLD_RF    = 4   # n ≤ ini: RF only
THRESHOLD_BLEND = 8   # n ≤ ini: blend RF + mean; else: mean only

# ===============================
# SEGMENT DISTANCE
# ===============================
segment_distance = {
    1: 550,  2: 270,  3: 200,  4: 450,  5: 200,
    6: 1200, 7: 650,  8: 850,  9: 700,  10: 1200,
    11: 850, 12: 1200, 13: 400, 14: 800, 15: 450
}

ALL_HALTE = list(range(2, 17))
#pada phyton range halte (a.b) b tidak akan di hitung karena sebagai stop, kalau di tulis (2,16) halte 16 tidak ada ETA nya
# daftar semua halte tujuan yang akan dihitung ETA-nya untuk setiap posisi bus. Halte 1 tidak masuk karena itu titik awal rute


# ===============================
# EWMA SMOOTHING (per bus)
# ===============================
ALPHA       = 0.35
eta_history = {}   # key: "{bus}_{target}" — unik per bus

def ewma_update(key, new_val):
    if key not in eta_history or eta_history[key] is None: 
        eta_history[key] = new_val
        return new_val
    smoothed         = ALPHA * new_val + (1 - ALPHA) * eta_history[key]
    eta_history[key] = smoothed
    return smoothed
# eta_histori -> dictionary yang menyimpan nilai ETA terakhir per kombinasi bus+halte. Kunci: '{bus}_{target}' Misal: 'bus_1_5', 'bus_2_10'

# ===============================
# ROLLING STOP INTENSITY (per bus)
# ===============================
# Masing-masing bus punya buffer sendiri agar tidak saling mempengaruhi
stop_buffers = {bus: deque(maxlen=10) for bus in BUS_LIST}

def compute_stop_intensity(bus, speed):
    stop_buffers[bus].append(1 if speed < 3 else 0)
    buf = stop_buffers[bus]
    return sum(buf) / len(buf) #perhitungan untuk stop misal 6/10= 0,6

# ===============================
# TRAFFIC LEVEL
# ===============================
def compute_traffic_level(speed, hour):
    if speed < 5:
        return 3
    elif speed < 15:
        return 2
    elif (6 <= hour < 9) or (16 <= hour < 19):
        return 1
    return 0

def is_rush_hour(hour):
    return 1 if (6 <= hour < 9) or (16 <= hour < 19) else 0

# ===============================
# CUMULATIVE HISTORICAL ETA (Fungsi ini menghitung total ETA historis dari halte_asal ke halte_tujuan dengan menjumlahkan ETA per segmen)
# ===============================
def compute_cumulative_eta(halte_asal, halte_tujuan):
    total = 0.0
    for h in range(halte_asal, halte_tujuan):
        seg_key = f"{h}_{h+1}"
        if seg_key in seg_avg_eta:
            total += seg_avg_eta[seg_key]
        elif seg_key in segment_profile:
            spd   = max(segment_profile[seg_key].get('seg_avg_speed', 20), 5)
            dist  = segment_distance.get(h, 400)
            total += (dist / 1000) / spd * 60
        else:
            total += 2.0
    return total

# ===============================
# PREDICT ETA — HYBRID
# ===============================
FEATURES = [
    'speed', 'traffic_level', 'stop_intensity', 'progress',
    'halte_asal_id', 'halte_tujuan_target',
    'n_segments', 'cumulative_eta_hist', 'is_rush_hour'
]

def predict_eta(speed, hour, stop_intensity, distance_to_target,
                halte_asal, target):
    if halte_asal >= target:
        return 0.0

    seg_dist   = segment_distance.get(halte_asal, 300)
    progress   = min(1.0, max(0.0, distance_to_target / seg_dist)) if seg_dist > 0 else 0.0
    traffic    = compute_traffic_level(speed, hour)
    n_segments = target - halte_asal
    cum_eta    = compute_cumulative_eta(halte_asal, target)
    rush       = is_rush_hour(hour)

    if cum_eta <= 0:
        return 2.0 * n_segments

    mean_r = mean_ratio_per_n.get(n_segments,
             mean_ratio_per_n.get(max(mean_ratio_per_n.keys()), 0.90))

    feat = {
        'speed':                speed,
        'traffic_level':        traffic,
        'stop_intensity':       stop_intensity,
        'progress':             progress,
        'halte_asal_id':        halte_asal,
        'halte_tujuan_target':  target,
        'n_segments':           n_segments,
        'cumulative_eta_hist':  cum_eta,
        'is_rush_hour':         rush
    }

    if n_segments <= THRESHOLD_RF:
        # RF saja
        row    = pd.DataFrame([feat])
        rf_r   = np.clip(model.predict(row)[0], 0.3, 3.0)
        ratio  = rf_r

    elif n_segments <= THRESHOLD_BLEND:
        # Blend 50/50
        row    = pd.DataFrame([feat])
        rf_r   = np.clip(model.predict(row)[0], 0.3, 3.0)
        ratio  = 0.50 * rf_r + 0.50 * mean_r

    else:
        # Mean rasio historis saja (RF tidak reliabel untuk n besar)
        ratio  = mean_r

    pred_min = ratio * cum_eta

    # Floor fisika
    total_dist = sum(segment_distance.get(h, 300) for h in range(halte_asal, target))
    min_eta    = (total_dist / 1000) / 80 * 60
    pred_min   = max(pred_min, min_eta)

    # Floor/ceiling historis
    pred_min = max(pred_min, cum_eta * 0.4) #minimal 40% histori ga boleh terllau cepat kadang RF meremehkan kondisi jalan
    pred_min = min(pred_min, cum_eta * 2.5) #maks ETA 250% dari histori ga boleh kelamaan

    return max(0.0, pred_min) #pastikan ETA tidak pernah negatif

# ===============================
# FORMAT ETA
# ===============================
def format_eta(eta_menit):
    total_detik = round(eta_menit * 60)
    menit       = total_detik // 60
    detik       = total_detik % 60
    if menit == 0:
        return f"{detik} detik"
    elif detik == 0:
        return f"{menit} menit"
    return f"{menit} menit {detik} detik"

def strategi_label(n):
    if n <= THRESHOLD_RF:
        return "RF"
    elif n <= THRESHOLD_BLEND:
        return "blend"
    return "hist"

# ===============================
# LOG CSV
# ===============================
csv_file   = open("eta_rt_v2_RFvsGmaps.csv", "a", newline="")
csv_writer = csv.writer(csv_file)
csv_writer.writerow(["timestamp", "bus", "halte_asal", "target",
                     "eta_raw", "eta_smoothed", "cum_eta", "strategi"])

# ===============================
# LOOP UTAMA
# ===============================
print("=" * 50)
print("ETA REALTIME — HYBRID MODEL v4 (MULTI-BUS)")
print("=" * 50)
print(f"Bus aktif  : {', '.join(BUS_LIST)}")
print(f"Strategi   : n≤{THRESHOLD_RF}=RF | {THRESHOLD_RF}<n≤{THRESHOLD_BLEND}=blend | n>{THRESHOLD_BLEND}=hist")
print("\nVerifikasi cumulative_eta_hist:")
for h_a, h_t in [(1, 5), (1, 10), (1, 16)]:
    ce = compute_cumulative_eta(h_a, h_t)
    mr = mean_ratio_per_n.get(h_t - h_a, 0.90)
    print(f"  H{h_a}→H{h_t}: hist={ce:.2f}mnt, mean_ratio={mr:.3f}, "
          f"pred≈{ce * mr:.1f}mnt")

while True:
    hour = datetime.now().hour

    for bus in BUS_LIST:
        # ---------------------------
        # 1. Ambil data dari Firebase
        # ---------------------------
        try:
            ref  = db.reference("percobaan_5").child(bus)
            data = ref.order_by_key().limit_to_last(10).get()
        except Exception as e:
            print(f"[{bus}] Firebase error: {e}")
            continue   # lanjut ke bus berikutnya

        if not data:
            print(f"[{bus}] Tidak ada data, dilewati.")
            continue

        # ---------------------------
        # 2. Parsing data bus
        # ---------------------------
        data_list  = list(data.values())
        bus_data   = data_list[-1]
        speeds     = [d.get("speed", 0) for d in data_list]
        speed_med  = float(np.median(speeds))

        halte_asal         = bus_data.get("halte_asal_id", 1)
        distance_to_target = bus_data.get("distance_to_target", 0)

        # Koreksi kecepatan jika bus berhenti
        if speed_med < 2:
            seg_key   = f"{halte_asal}_{halte_asal + 1}"
            seg_data  = segment_profile.get(seg_key)
            speed_used = seg_data['seg_avg_speed'] * 0.4 if seg_data else 5.0
        else:
            speed_used = speed_med

        stop_intensity = compute_stop_intensity(bus, speed_used)

        # ---------------------------
        # 3. Hitung ETA semua halte
        # ---------------------------
        hasil_bus = {}
        for target in ALL_HALTE:
            if halte_asal >= target:
                eta_final = 0.0
            else:
                eta_raw   = predict_eta(
                    speed_used, hour, stop_intensity,
                    distance_to_target, halte_asal, target
                )
                key       = f"{bus}_{target}"
                eta_final = ewma_update(key, eta_raw)

                n_seg = target - halte_asal
                cum   = compute_cumulative_eta(halte_asal, target)
                now   = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                csv_writer.writerow([
                    now, bus, halte_asal, target,
                    round(eta_raw, 2), round(eta_final, 2),
                    round(cum, 2), strategi_label(n_seg)
                ])

            hasil_bus[f"halte_{target}"] = round(eta_final, 2)

        # ---------------------------
        # 4. Kirim hasil ke Firebase
        # ---------------------------
        try:
            db.reference("eta_result").child(bus).set(hasil_bus)
        except Exception as e:
            print(f"[{bus}] Gagal kirim ke Firebase: {e}")
            continue

        # ---------------------------
        # 5. Print ringkasan ke konsol
        # ---------------------------
        traffic_now = compute_traffic_level(speed_used, hour)
        print(f"\n[{datetime.now().strftime('%H:%M:%S')}] "
              f"{bus} | asal:{halte_asal} | speed:{speed_used:.1f} | "
              f"stop:{stop_intensity:.2f} | traffic:{traffic_now} | "
              f"rush:{is_rush_hour(hour)}")
        for k, v in hasil_bus.items():
            if v > 0:
                tgt   = int(k.split('_')[1])
                n     = tgt - halte_asal
                strat = strategi_label(n)
                cum   = compute_cumulative_eta(halte_asal, tgt)
                print(f"  → {k}: {format_eta(v):20s} "
                      f"[{strat:5s} | hist={cum:.1f}mnt]")

    csv_file.flush()
    time.sleep(5)