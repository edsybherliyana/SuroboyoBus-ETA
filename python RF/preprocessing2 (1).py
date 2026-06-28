# =======================================================================
# FILE 1: PREPROCESSING v2
# Perubahan kritis dari v1:
#  - Tambah Step 9: bangun dataset multi-hop
#    Setiap baris GPS → N pasang (baris, halte_tujuan) dengan ETA nyata
#  - Tambah fitur: n_segments, cumulative_eta_hist
#  - Output: prep_multihop.csv (untuk training v2)
#  - prep_fixed.csv tetap disimpan untuk kompatibilitas
# =======================================================================

import pandas as pd
import numpy as np
import joblib

# ===============================
# SEGMENT DISTANCE (METER)
# ===============================
segment_distance = {
    1: 550,  2: 270,  3: 200,  4: 450,  5: 200,
    6: 1200, 7: 650,  8: 850,  9: 700,  10: 1200,
    11: 850, 12: 1200, 13: 400, 14: 800, 15: 450
}

# ===============================
# 1. LOAD DATA
# ===============================
df = pd.read_csv("backend3final.csv")
print("Data awal:", df.shape)

df['time'] = pd.to_datetime(df['time'], format='%H:%M:%S')
df = df.sort_values(by='time').reset_index(drop=True)

# ===============================
# 2. ETA REAL DARI ARRIVAL
# ===============================
df['eta_real'] = None
arrival_idx = df.index[df['arrival'] == 1].tolist()

for i in range(len(arrival_idx)):
    start = arrival_idx[i-1] + 1 if i > 0 else 0
    end   = arrival_idx[i]
    arrival_time = df.loc[end, 'time']
    df.loc[start:end, 'eta_real'] = (
        (arrival_time - df.loc[start:end, 'time']).dt.total_seconds() / 60
    )

df = df.dropna(subset=['eta_real'])

# ===============================
# 3. TIME FEATURE
# ===============================
df['hour']   = df['time'].dt.hour
df['minute'] = df['time'].dt.minute

# ===============================
# 4. TRAFFIC LEVEL
# ===============================
df['traffic_level'] = np.where(
    df['speed'] < 5, 3,
    np.where(df['speed'] < 15, 2,
    np.where(((df['hour'] >= 6) & (df['hour'] <= 8)) |
             ((df['hour'] >= 16) & (df['hour'] <= 18)), 1, 0))
)

# ===============================
# 5. STOP INTENSITY
# ===============================
df['is_stop']        = (df['speed'] < 3).astype(int)
df['stop_count_10']  = df['is_stop'].rolling(10, min_periods=1).sum()
df['stop_intensity'] = df['stop_count_10'] / 10

# ===============================
# 6. PROGRESS
# ===============================
df['seg_dist'] = df['halte_asal_id'].map(segment_distance)
df['seg_dist'] = df['seg_dist'].fillna(df['distance_to_target'])
df['progress'] = (df['distance_to_target'] / df['seg_dist']).clip(0, 1).fillna(0)

# ===============================
# 7. SEGMENT PROFILE
# ===============================
df['segment_id_str'] = (df['halte_asal_id'].astype(str) + "_" +
                        df['halte_tujuan_id'].astype(str))

segment_stats = df.groupby('segment_id_str').agg(
    seg_avg_speed   = ('speed',         'mean'),
    seg_avg_traffic = ('traffic_level', 'mean'),
    seg_avg_stop    = ('stop_intensity','mean'),
    seg_avg_eta     = ('eta_real',      'mean'),
    seg_dist        = ('seg_dist',      'first')
).reset_index()

segment_stats.to_csv("segment_profilepre2.csv", index=False)
print("✅ segment_profilepre2.csv disimpan")

# ===============================
# 8. SEG_AVG_ETA DARI ARRIVAL NYATA
# ===============================
seg_times = {}
for i in range(1, len(arrival_idx)):
    prev_end = arrival_idx[i-1]
    curr_end = arrival_idx[i]

    h_asal   = df.loc[prev_end, 'halte_tujuan_id'] if prev_end in df.index else None
    h_tujuan = df.loc[curr_end, 'halte_tujuan_id'] if curr_end in df.index else None

    if h_asal is None or h_tujuan is None:
        continue

    duration = (df.loc[curr_end, 'time'] - df.loc[prev_end, 'time']).total_seconds() / 60

    if 0 < duration <= 60:
        seg_key = f"{int(h_asal)}_{int(h_tujuan)}"
        seg_times.setdefault(seg_key, []).append(duration)

seg_avg_eta_real = {k: float(np.mean(v)) for k, v in seg_times.items()}
joblib.dump(seg_avg_eta_real, "seg_avg_etapre2.pkl")

print("\n✅ seg_avg_etapre2.pkl disimpan:")
for k in sorted(seg_avg_eta_real):
    print(f"  {k}: {seg_avg_eta_real[k]:.2f} menit")

# Simpan juga prep_fixed.csv (backward compat)
df.to_csv("prep_fixedpre2.csv", index=False)
print(f"\n✅ prep_fixedpre2.csv: {df.shape}")

# ===============================
# 9. BUILD MULTI-HOP TRAINING DATASET  ← PERUBAHAN UTAMA
#
# Arsitektur lama: tiap baris GPS → prediksi 1 segmen → error bertumpuk
# Arsitektur baru: tiap baris GPS → N pasang (baris, halte_tujuan)
#   dengan ground truth ETA nyata ke tiap halte tujuan
#
# Fitur tambahan per pasang:
#   - halte_tujuan_target : halte tujuan prediksi
#   - n_segments          : jumlah segmen tersisa (tujuan - asal)
#   - cumulative_eta_hist : Σ seg_avg_eta dari asal ke tujuan
#                           (anchor historis yang kuat untuk RF)
# ===============================
print("\nMembangun dataset multi-hop...")

# Buat lookup: setiap arrival event → (waktu, halte)
# Diurutkan berdasarkan waktu
arrival_list = sorted([
    (df.loc[idx, 'time'], int(df.loc[idx, 'halte_tujuan_id']))
    for idx in arrival_idx
])

df_multi_rows = []
skipped = 0

for i, row in df.iterrows():
    t_now  = row['time']
    h_asal = int(row['halte_asal_id'])

    # Cari arrival berikutnya untuk tiap halte tujuan setelah t_now
    # "Berikutnya" = pertama kali halte itu dicapai setelah t_now
    seen_haltes = {}
    for t_arr, h_tgt in arrival_list:
        if t_arr > t_now and h_tgt > h_asal and h_tgt not in seen_haltes:
            seen_haltes[h_tgt] = t_arr

    for h_target, t_arrival in seen_haltes.items():
        eta = (t_arrival - t_now).total_seconds() / 60

        # Filter: buang outlier dan noise
        if eta < 0.05 or eta > 60:
            skipped += 1
            continue

        # Fitur baru: cumulative historical ETA ke target
        cum_eta = sum(
            seg_avg_eta_real.get(f"{h}_{h+1}", 2.0)
            for h in range(h_asal, h_target)
        )

        r = row.to_dict()
        r['halte_tujuan_target'] = h_target
        r['eta_to_target']       = eta
        r['n_segments']          = h_target - h_asal
        r['cumulative_eta_hist'] = cum_eta
        df_multi_rows.append(r)

df_multi = pd.DataFrame(df_multi_rows)
df_multi.to_csv("prep_multihoppre2.csv", index=False)

print(f"✅ prep_multihoppre2.csv: {df_multi.shape}")
print(f"   Baris dilewati (outlier): {skipped}")
print(f"   Avg baris per halte_asal: "
      f"{df_multi.shape[0]/df['halte_asal_id'].nunique():.0f}")

# Cek distribusi cumulative_eta_hist
print(f"\nDistribusi cumulative_eta_hist (anchor historis):")
print(df_multi.groupby('n_segments')['cumulative_eta_hist'].mean().round(2).to_string())