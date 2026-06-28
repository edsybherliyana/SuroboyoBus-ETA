package com.example.betracking;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.database.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapsActivity2 extends FragmentActivity
        implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker bus1Marker, bus2Marker, passengerMarker;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    // TextView ETA penumpang
    private TextView textETA_bus1, textETA_bus2, textHalte;
    // TextView tujuan bus realtime
    private TextView textBus1Tujuan, textBus2Tujuan;

    // Snapshot ETA terakhir
    private DataSnapshot lastEtaSnapshot = null;

    // Tujuan halte bus (index 0-based, -1 = belum diketahui)
    private int bus1TujuanIndex = -1;
    private int bus2TujuanIndex = -1;

    // ================= DATA HALTE =================
    private final LatLng[] halte = {
            new LatLng(-7.264030678833229, 112.7831782435129),   // 1
            new LatLng(-7.26896355554142,  112.7825184200965),   // 2
            new LatLng(-7.27006506429655,  112.78358862129653),  // 3
            new LatLng(-7.270285897855766, 112.78540984141164),  // 4
            new LatLng(-7.270762152987534, 112.78905764564608),  // 5
            new LatLng(-7.270802062610386, 112.78753146870301),  // 6
            new LatLng(-7.275594547498979, 112.78158033583237),  // 7
            new LatLng(-7.280255222485801, 112.78196722614798),  // 8
            new LatLng(-7.279258,          112.789785),          // 9
            new LatLng(-7.2755785729777545,112.79316892447837),  // 10
            new LatLng(-7.275714181181243, 112.79338849910401),  // 11
            new LatLng(-7.279522360135274, 112.79000011749176),  // 12
            new LatLng(-7.279352649012284, 112.78073999536396),  // 13
            new LatLng(-7.2762640,         112.7810240),         // 14
            new LatLng(-7.268991785364545, 112.78221540193282),  // 15
            new LatLng(-7.264926221275623, 112.78279941563315)   // 16
    };

    private final String[] halteNames = {
            "Mulyorejo B",          // 1
            "UNAIR B",              // 2
            "UNAIR Kampus C",       // 3
            "RS UNAIR",             // 4
            "Suterejo Barat",       // 5
            "Dharmahusada Regency", // 6
            "Galaxy B",             // 7
            "Kertajaya Indah",      // 8
            "Bundaran ITS",         // 9
            "PENS B",               // 10
            "PENS A",               // 11
            "ITS",                  // 12
            "KONI MERR",            // 13
            "Galaxy A",             // 14
            "UNAIR A",              // 15
            "Airlangga Convention Center" // 16
    };

    // ================= PENUMPANG =================
    private LatLng passengerPosition;
    private int nearestHalteIndex = 0;
    private int lastHalteIndex = -1;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    // ================= ETA DISPLAY =================
    private void updateETADisplay(DataSnapshot snapshot) {
        if (snapshot == null) return;

        String halteKey = "halte_" + (nearestHalteIndex + 1);

        // Bus 1
        Object rawEta1 = snapshot.child("bus_1").child(halteKey).getValue();
        double eta1 = (rawEta1 instanceof Number) ? ((Number) rawEta1).doubleValue() : 0.0;

        // Bus 2
        Object rawEta2 = snapshot.child("bus_2").child(halteKey).getValue();
        double eta2 = (rawEta2 instanceof Number) ? ((Number) rawEta2).doubleValue() : 0.0;

        // Nama halte terdekat penumpang
        String namaHalte = halteNames[nearestHalteIndex];
        textHalte.setText("Menuju Halte : " + namaHalte);

        textETA_bus1.setText(String.format(java.util.Locale.US,
                "Bus 1 ETA : %.1f menit", eta1));

        textETA_bus2.setText(String.format(java.util.Locale.US,
                "Bus 2 ETA : %.1f menit", eta2));
    }

    private void listenETARealtime() {
        FirebaseDatabase.getInstance()
                .getReference("eta_result")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        lastEtaSnapshot = snapshot;
                        updateETADisplay(snapshot);
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("ETA", "Error: " + error.getMessage());
                    }
                });
    }

    // ================= BUS TUJUAN REALTIME =================
    private void updateBusTujuanDisplay(boolean isBus1, int halteId) {
        // halteId dari Firebase adalah 1-based
        String nama = (halteId >= 1 && halteId <= halteNames.length)
                ? halteNames[halteId - 1]
                : "Tidak diketahui";

        if (isBus1) {
            bus1TujuanIndex = halteId - 1;
            textBus1Tujuan.setText("Bus 1 menuju : " + nama);
        } else {
            bus2TujuanIndex = halteId - 1;
            textBus2Tujuan.setText("Bus 2 menuju : " + nama);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps2);

        textETA_bus1    = findViewById(R.id.textETA_bus1);
        textETA_bus2    = findViewById(R.id.textETA_bus2);
        textHalte       = findViewById(R.id.textHalte);
        textBus1Tujuan  = findViewById(R.id.textBus1Tujuan);
        textBus2Tujuan  = findViewById(R.id.textBus2Tujuan);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Marker halte
        for (int i = 0; i < halte.length; i++) {
            mMap.addMarker(new MarkerOptions()
                    .position(halte[i])
                    .title(halteNames[i])
                    .icon(BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_ROSE)));
        }

        // Rute
        List<LatLng> routePoints = new ArrayList<>();
        routePoints.add(new LatLng(-7.264030678833229, 112.7831782435129));
        routePoints.add(new LatLng(-7.268963555541420, 112.7825184200965));
        routePoints.add(new LatLng(-7.269944,          112.782269));
        routePoints.add(new LatLng(-7.270065064296550, 112.78358862129653));
        routePoints.add(new LatLng(-7.270285897855766, 112.78540984141164));
        routePoints.add(new LatLng(-7.270762152987534, 112.78905764564608));
        routePoints.add(new LatLng(-7.270837,          112.789296));
        routePoints.add(new LatLng(-7.271014,          112.789283));
        routePoints.add(new LatLng(-7.270802062610386, 112.78753146870301));
        routePoints.add(new LatLng(-7.270153,          112.782245));
        routePoints.add(new LatLng(-7.274468,          112.781863));
        routePoints.add(new LatLng(-7.275594547498979, 112.78158033583237));
        routePoints.add(new LatLng(-7.277339,          112.781021));
        routePoints.add(new LatLng(-7.280433,          112.781030));
        routePoints.add(new LatLng(-7.279258,          112.789785));
        routePoints.add(new LatLng(-7.279190,          112.789925));
        routePoints.add(new LatLng(-7.278994,          112.790085));
        routePoints.add(new LatLng(-7.276734,          112.789889));
        routePoints.add(new LatLng(-7.276160,          112.790348));
        routePoints.add(new LatLng(-7.2755785729777545,112.79316892447837));
        routePoints.add(new LatLng(-7.274514,          112.797498));
        routePoints.add(new LatLng(-7.274294,          112.797764));
        routePoints.add(new LatLng(-7.274338,          112.798001));
        routePoints.add(new LatLng(-7.274587,          112.798090));
        routePoints.add(new LatLng(-7.274780,          112.797944));
        routePoints.add(new LatLng(-7.274762,          112.797634));
        routePoints.add(new LatLng(-7.275714181181243, 112.79338849910401));
        routePoints.add(new LatLng(-7.276494,          112.790501));
        routePoints.add(new LatLng(-7.276909,          112.790292));
        routePoints.add(new LatLng(-7.278798,          112.790427));
        routePoints.add(new LatLng(-7.279049,          112.790864));
        routePoints.add(new LatLng(-7.279449,          112.790746));
        routePoints.add(new LatLng(-7.279647,          112.790416));
        routePoints.add(new LatLng(-7.279490820849154, 112.79000086948435));
        routePoints.add(new LatLng(-7.279523,          112.789688));
        routePoints.add(new LatLng(-7.280690,          112.781024));
        routePoints.add(new LatLng(-7.280672,          112.780730));
        routePoints.add(new LatLng(-7.279352649012284, 112.78073999536396));
        routePoints.add(new LatLng(-7.277427,          112.780742));
        routePoints.add(new LatLng(-7.2762640,         112.7810240));
        routePoints.add(new LatLng(-7.274683,          112.781504));
        routePoints.add(new LatLng(-7.268991785364545, 112.78221540193282));
        routePoints.add(new LatLng(-7.264926221275623, 112.78279941563315));
        drawRealisticRoad(routePoints);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        listenAllBusRealtime();
        startPassengerLocationRealtime();
        listenETARealtime();
    }

    private void drawRealisticRoad(List<LatLng> routePoints) {
        mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints).width(18f).color(Color.BLACK).zIndex(1f)
                .startCap(new RoundCap()).endCap(new RoundCap())
                .jointType(JointType.ROUND));
        mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints).width(12f)
                .color(Color.parseColor("#2C2C2C")).zIndex(2f)
                .startCap(new RoundCap()).endCap(new RoundCap())
                .jointType(JointType.ROUND));
        mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints).width(4f).color(Color.WHITE)
                .pattern(Arrays.asList(new Dash(20), new Gap(20))).zIndex(3f)
                .startCap(new RoundCap()).endCap(new RoundCap())
                .jointType(JointType.ROUND));
    }

    // ================= BUS LISTENER =================
    private void listenAllBusRealtime() {
        listenSingleBus("bus_1", true);
        listenSingleBus("bus_2", false);
    }

    private void listenSingleBus(String busId, boolean isBus1) {
        FirebaseDatabase.getInstance()
                .getReference("percobaan_5")
                .child(busId)
                .limitToLast(1)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (!snapshot.exists()) return;
                        for (DataSnapshot data : snapshot.getChildren()) {

                            Object latObj  = data.child("latitude_rt").getValue();
                            Object lonObj  = data.child("longitude_rt").getValue();
                            Object bearObj = data.child("bearing").getValue();

                            // *** Baca halte tujuan bus realtime ***
                            Object tujuanObj = data.child("halte_tujuan_id").getValue();
                            if (tujuanObj instanceof Number) {
                                int tujuanId = ((Number) tujuanObj).intValue();
                                updateBusTujuanDisplay(isBus1, tujuanId);
                            }

                            if (latObj instanceof Number && lonObj instanceof Number) {
                                double lat    = ((Number) latObj).doubleValue();
                                double lon    = ((Number) lonObj).doubleValue();
                                float bearing = bearObj instanceof Number
                                        ? ((Number) bearObj).floatValue() : 0f;
                                updateBus(lat, lon, bearing, isBus1);
                            }
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        android.util.Log.e("BUS", "Error: " + error.getMessage());
                    }
                });
    }

    private void updateBus(double lat, double lon, float bearing, boolean isBus1) {
        LatLng pos    = new LatLng(lat, lon);
        Marker marker = isBus1 ? bus1Marker : bus2Marker;

        if (marker == null) {
            marker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(isBus1 ? "Bus 1" : "Bus 2")
                    .icon(BitmapDescriptorFactory.fromResource(
                            isBus1 ? R.drawable.bus : R.drawable.bus1))
                    .rotation(bearing)
                    .anchor(0.5f, 0.5f)
                    .flat(true));
            if (isBus1) bus1Marker = marker;
            else        bus2Marker = marker;
        } else {
            marker.setPosition(pos);
            marker.setRotation(bearing);
        }
    }

    // ================= PASSENGER =================
    private void sendPassengerHalteToFirebase() {
        FirebaseDatabase.getInstance()
                .getReference("passenger")
                .child("halte_id")
                .setValue(nearestHalteIndex + 1);
    }

    private void startPassengerLocationRealtime() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        LocationRequest request =
                new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                        .setMinUpdateIntervalMillis(2000)
                        .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) return;

                passengerPosition = new LatLng(loc.getLatitude(), loc.getLongitude());

                if (passengerMarker == null) {
                    passengerMarker = mMap.addMarker(new MarkerOptions()
                            .position(passengerPosition)
                            .title("Penumpang")
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.orang))
                            .anchor(0.5f, 0.5f)
                            .flat(true));
                    mMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(passengerPosition, 16f));
                } else {
                    passengerMarker.setPosition(passengerPosition);
                }

                // Cari halte terdekat penumpang
                nearestHalteIndex = 0;
                float minDistance = Float.MAX_VALUE;
                for (int i = 0; i < halte.length; i++) {
                    Location halteLoc = new Location("halte");
                    halteLoc.setLatitude(halte[i].latitude);
                    halteLoc.setLongitude(halte[i].longitude);
                    float dist = halteLoc.distanceTo(loc);
                    if (dist < minDistance) {
                        minDistance   = dist;
                        nearestHalteIndex = i;
                    }
                }

                if (nearestHalteIndex != lastHalteIndex) {
                    lastHalteIndex = nearestHalteIndex;
                    sendPassengerHalteToFirebase();
                    // Refresh ETA sesuai halte baru
                    updateETADisplay(lastEtaSnapshot);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                request, locationCallback, getMainLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}