# =======================================================================
# FILE 2: TRAINING v4 — HYBRID MODEL
# Perubahan dari v3:
#  - Hapus 'hour' (menyebabkan cluster bias 31% importance)
#  - Ganti dengan 'is_rush_hour' (binary) — lebih bersih
#  - Hitung mean_ratio per n_segments dari training → simpan ke pkl
#  - RF hanya dilatih untuk n_segments <= 5 (terbukti efektif)
#  - Untuk n > 5: prediksi = cum_hist × mean_ratio[n] (lookup)
#  - Output: model_eta_v4.pkl + mean_ratio_per_n.pkl
# =======================================================================

import pandas as pd
import numpy as np
import joblib
import matplotlib.pyplot as plt
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error

# ===============================
# 1. LOAD DATA
# ===============================
df = pd.read_csv("prep_multihoppre2.csv")
print(f"Multi-hop data: {df.shape[0]:,} baris")

df = df[df['eta_to_target'] >= 0.05].copy()
df = df[df['halte_tujuan_target'] <= 16].copy()
df = df[df['halte_tujuan_target'] > df['halte_asal_id']].copy()
df = df[df['cumulative_eta_hist'] > 0.1].copy()
print(f"Setelah filter dasar: {df.shape[0]:,} baris")

# ===============================
# 2. HITUNG RASIO
# ===============================
df['eta_ratio'] = df['eta_to_target'] / df['cumulative_eta_hist']
df = df[(df['eta_ratio'] >= 0.3) & (df['eta_ratio'] <= 3.0)].copy()
print(f"Setelah clip rasio [0.3, 3.0]: {df.shape[0]:,} baris")

# ===============================
# 3. FITUR — TANPA hour, PAKAI is_rush_hour
# Kenapa hapus hour?
#   Setiap GPS reading menghasilkan N baris multi-hop dengan hour identik.
#   RF menggunakan hour sebagai proxy cluster sesi GPS (31% importance),
#   bukan sebagai proxy traffic yang sesungguhnya → bias.
# Ganti dengan is_rush_hour (binary) yang lebih bersih secara semantik.
# ===============================
if 'hour' not in df.columns:
    if 'time' in df.columns:
        df['time'] = pd.to_datetime(df['time'], format='%H:%M:%S', errors='coerce')
        df['hour'] = df['time'].dt.hour
    else:
        df['hour'] = 8

df['is_rush_hour'] = df['hour'].apply(
    lambda h: 1 if (6 <= h < 9) or (16 <= h < 19) else 0
).astype(int)

FEATURES = [
    'speed',
    'traffic_level',
    'stop_intensity',
    'progress',
    'halte_asal_id',
    'halte_tujuan_target',
    'n_segments',
    'cumulative_eta_hist',
    'is_rush_hour'       # ganti hour dengan binary rush hour
]

# ===============================
# 4. SPLIT BERBASIS WAKTU
# ===============================
if 'time' in df.columns:
    df = df.sort_values('time').reset_index(drop=True)
    cut = int(len(df) * 0.80)
    df_train = df.iloc[:cut].copy()
    df_val   = df.iloc[cut:].copy()
else:
    from sklearn.model_selection import train_test_split
    df_train, df_val = train_test_split(df, test_size=0.2, random_state=42)

print(f"\nSplit: train={len(df_train):,} | val={len(df_val):,}")

# ===============================
# 5. HITUNG MEAN RATIO PER N_SEGMENTS DARI TRAINING
# Ini adalah fallback terbaik untuk n besar.
# Disimpan ke pkl agar bisa dipakai di inference.
# ===============================
mean_ratio_per_n = df_train.groupby('n_segments')['eta_ratio'].mean().to_dict()
std_ratio_per_n  = df_train.groupby('n_segments')['eta_ratio'].std().to_dict()

print(f"\nMean rasio per n_segments (dari training):")
print(f"{'n':>4}  {'mean':>7}  {'std':>7}  {'strategi':>15}")
print("-" * 42)
for n in sorted(mean_ratio_per_n.keys()):
    strat = "RF only" if n <= 4 else ("blend RF+mean" if n <= 8 else "mean only")
    print(f"{n:>4}  {mean_ratio_per_n[n]:>7.4f}  "
          f"{std_ratio_per_n.get(n,0):>7.4f}  {strat:>15}")

joblib.dump(mean_ratio_per_n, "mean_ratio_per_npre2.pkl")
print(f"\n✅ mean_ratio_per_npre2.pkl disimpan")

# ===============================
# 6. TRAIN RF HANYA UNTUK n_segments <= 5
# Untuk n > 5, RF terbukti lebih buruk dari prediksi mean rasio.
# ===============================
THRESHOLD_RF = 5    # n <= THRESHOLD_RF → pakai RF

df_train_rf = df_train[df_train['n_segments'] <= THRESHOLD_RF].copy()
print(f"\nData training RF (n ≤ {THRESHOLD_RF}): {len(df_train_rf):,} baris")

X_train = df_train_rf[FEATURES].fillna(0)
y_train = df_train_rf['eta_ratio']

model = RandomForestRegressor(
    n_estimators=600,
    max_depth=16,
    min_samples_leaf=3,
    max_features='sqrt',
    random_state=42,
    n_jobs=-1
)
print("Training RF (n ≤ 5)...")
model.fit(X_train, y_train)
print("✅ Training selesai")

# ===============================
# 7. EVALUASI — HYBRID PREDICTION
# Strategi berdasarkan n_segments:
#   n ≤ 4 : RF saja
#   5 ≤ n ≤ 8 : 50% RF + 50% mean rasio
#   n > 8  : mean rasio saja
# ===============================
def hybrid_predict_ratio(row_features, n, cum_eta):
    mean_r = mean_ratio_per_n.get(n, 0.90)

    if n <= 4:
        # Pure RF
        row_df = pd.DataFrame([row_features])
        rf_r   = model.predict(row_df)[0]
        return np.clip(rf_r, 0.3, 3.0)

    elif n <= 8:
        # Blend: 50% RF + 50% mean
        row_df = pd.DataFrame([row_features])
        rf_r   = model.predict(row_df)[0]
        rf_r   = np.clip(rf_r, 0.3, 3.0)
        return 0.50 * rf_r + 0.50 * mean_r

    else:
        # Mean saja — RF tidak reliabel
        return mean_r


def thr_acc(yt, yp, t):
    return np.mean(np.abs(yt - yp) <= t) * 100


print(f"\nEvaluasi hybrid prediction di validation set...")
X_val = df_val[FEATURES].fillna(0)

y_pred_list = []
for i, (idx, row) in enumerate(X_val.iterrows()):
    feat = {f: row[f] for f in FEATURES}
    n    = int(row['n_segments'])
    cum  = row['cumulative_eta_hist']
    r    = hybrid_predict_ratio(feat, n, cum)
    y_pred_list.append(r * cum)

y_pred = np.array(y_pred_list)
y_true = df_val['eta_to_target'].values
n_vals = df_val['n_segments'].values

mae  = mean_absolute_error(y_true, y_pred)
rmse = np.sqrt(mean_squared_error(y_true, y_pred))
smape = np.mean(
    2 * np.abs(y_pred - y_true) / (np.abs(y_true) + np.abs(y_pred) + 1e-6)
) * 100

print(f"\n{'='*55}")
print(f"EVALUASI KESELURUHAN (hybrid model v4)")
print(f"{'='*55}")
print(f"MAE              : {mae:.3f} menit")
print(f"RMSE             : {rmse:.3f} menit")
print(f"sMAPE            : {smape:.1f}%")
print(f"Akurasi ±30 detik: {thr_acc(y_true,y_pred,0.5):.1f}%")
print(f"Akurasi ±1 menit : {thr_acc(y_true,y_pred,1.0):.1f}%")
print(f"Akurasi ±2 menit : {thr_acc(y_true,y_pred,2.0):.1f}%")
print(f"{'='*55}")

# ===============================
# 8. METRIK PER N_SEGMENTS
# ===============================
print(f"\n===== METRIK PER N_SEGMENTS =====")
header = f"{'n':>3}  {'MAE':>7}  {'±1min%':>8}  {'strategi':>15}"
print(header)
print("-" * len(header))
for n in sorted(np.unique(n_vals)):
    mask = n_vals == n
    if mask.sum() < 5:
        continue
    sm   = mean_absolute_error(y_true[mask], y_pred[mask])
    sa   = thr_acc(y_true[mask], y_pred[mask], 1.0)
    strat = "RF only" if n <= 4 else ("blend" if n <= 8 else "mean only")
    print(f"{int(n):>3}  {sm:>7.3f}  {sa:>7.1f}%  {strat:>15}")

# ===============================
# 9. METRIK PER HALTE ASAL
# ===============================
h_asal_vals = df_val['halte_asal_id'].values
print(f"\n===== METRIK PER HALTE ASAL =====")
header2 = f"{'halte_asal':>10}  {'MAE':>7}  {'±1min%':>8}  {'N':>6}"
print(header2)
print("-" * len(header2))
for halte in sorted(np.unique(h_asal_vals)):
    mask = h_asal_vals == halte
    if mask.sum() < 5:
        continue
    sm = mean_absolute_error(y_true[mask], y_pred[mask])
    sa = thr_acc(y_true[mask], y_pred[mask], 1.0)
    print(f"{int(halte):>10}  {sm:>7.3f}  {sa:>7.1f}%  {mask.sum():>6}")

# ===============================
# 10. FEATURE IMPORTANCE (RF saja)
# ===============================
importances = pd.Series(
    model.feature_importances_, index=FEATURES
).sort_values(ascending=False)
print(f"\nFeature importance (RF untuk n ≤ 5):")
for feat, imp in importances.items():
    bar = "█" * int(imp * 40)
    print(f"  {feat:<22}: {imp:.4f}  {bar}")

# ===============================
# 10.5 VISUALISASI FEATURE IMPORTANCE
# ===============================
plt.figure(figsize=(10, 6))
# Mengurutkan dari yang terkecil ke terbesar agar yang tertinggi ada di atas saat di-plot barh
plot_data = importances.sort_values(ascending=True) 

colors = 'skyblue' # Warna biru muda seperti di contoh gambar
plt.barh(plot_data.index, plot_data.values, color=colors)

plt.title('Pengaruh Setiap Variabel terhadap ETA (Feature Importance)', fontsize=14, pad=15)
plt.xlabel('Tingkat Kepentingan', fontsize=12)
plt.ylabel('Fitur', fontsize=12)

# Menambahkan grid vertikal agar lebih mudah dibaca
plt.grid(axis='x', linestyle='--', alpha=0.7)

# Agar layout tidak terpotong
plt.tight_layout()

# Simpan sebagai gambar
plt.savefig("feature_importance_v4.png", dpi=150)
plt.show()

print(f"✅ feature_importance_v4.png disimpan")


# ===============================
# 11. GRAFIK
# ===============================
h_asals = sorted(df_val['halte_asal_id'].unique())
ncols   = 3
nrows   = int(np.ceil(len(h_asals) / ncols))
fig, axes = plt.subplots(nrows, ncols, figsize=(15, nrows * 3.5))
axes_flat = axes.flatten() if hasattr(axes, 'flatten') else [axes]

mae_by_pair = {}
for i, (idx, row) in enumerate(df_val.iterrows()):
    ha  = row['halte_asal_id']
    ht  = row['halte_tujuan_target']
    err = abs(y_true[i] - y_pred[i])
    mae_by_pair.setdefault((ha, ht), []).append(err)

for idx, halte in enumerate(h_asals):
    ax = axes_flat[idx]
    tujuans = sorted(k[1] for k in mae_by_pair if k[0] == halte)
    if not tujuans:
        ax.set_visible(False)
        continue
    maes = [np.mean(mae_by_pair[(halte, t)]) for t in tujuans]
    ax.plot(tujuans, maes, color='steelblue', marker='o',
            linewidth=1.5, markersize=4)
    ax.axhline(y=1.0, color='orange', linestyle='--', linewidth=0.8)
    ax.set_title(f"Halte Asal {int(halte)}", fontsize=11)
    ax.set_xlabel("Halte Tujuan")
    ax.set_ylabel("MAE (menit)")
    ax.grid(True, alpha=0.3)

for idx in range(len(h_asals), len(axes_flat)):
    axes_flat[idx].set_visible(False)

plt.suptitle("MAE per Halte Asal (Hybrid Model v2)", fontsize=13)
plt.tight_layout()
plt.savefig("mae_per_halte_v2.png", dpi=120, bbox_inches='tight')
plt.close()
print(f"\n✅ mae_per_halte_v2.png disimpan")

# ===============================
# 12. SIMPAN
# ===============================
joblib.dump(model, "model_eta_v2.pkl")
print(f"✅ model_eta_v2.pkl disimpan")
print(f"\n=== Pakai 3_inference_v2.py untuk realtime ===")