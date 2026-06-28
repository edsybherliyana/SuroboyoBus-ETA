package com.example.betracking;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapsActivity3 extends FragmentActivity implements OnMapReadyCallback {

    //private static final float RETARGET_DISTANCE = 200f;
    private String busId = "bus_2"; // default aman

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final float HALTE_RADIUS = 25f;  // 20–30 meter (aman GPS)

    private static final float MIN_SPEED_KMH = 2f;
    private static final float MIN_DISTANCE_METER = 3f;

    // ===== GPS WARM-UP (TAMBAHAN) =====
    private long gpsStartTime = 0;
    private static final long GPS_WARMUP_MS = 6000;
    // =================================

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Marker userMarker;

    private boolean firstZoomDone = false;
    private Location lastLocation;
    private long lastTime = 0;

    private DatabaseReference databaseRef;

    private boolean initialTargetSet = false;
    private boolean hasArrived = false;

    // ===== CHECKPOINT HALTE 10 -> 11 =====
    private final LatLng checkpoint1011 = new LatLng(-7.274574959267352, 112.79783330254087); // bundaran Pakuwon
    private boolean passedCheckpoint1011 = false;
    private static final float CHECKPOINT_RADIUS = 20f;

    // ================= DATA HALTE (1–16) =================
    private final LatLng[] halte = {
            new LatLng(-7.264030678833229, 112.7831782435129),
            new LatLng(-7.26896355554142, 112.7825184200965),
            new LatLng(-7.27006506429655, 112.78358862129653),
            new LatLng(-7.270285897855766, 112.78540984141164),
            new LatLng(-7.270762152987534, 112.78905764564608),
            new LatLng(-7.270802062610386, 112.78753146870301),
            new LatLng(-7.275594547498979, 112.78158033583237),
            new LatLng(-7.280255222485801, 112.78196722614798),
            new LatLng(-7.279258, 112.789785),
            new LatLng(-7.2755785729777545, 112.79316892447837),
            new LatLng(-7.275714181181243, 112.79338849910401),
            new LatLng(-7.279522360135274, 112.79000011749176),
            new LatLng(-7.279352649012284, 112.78073999536396),
            new LatLng(-7.2762640, 112.7810240),
            new LatLng(-7.268991785364545, 112.78221540193282),
            new LatLng(-7.264926221275623, 112.78279941563315)
    };

    private final String[] halteNames = {
            "Mulyorejo B", "UNAIR B", "UNAIR Kampus C", "RS UNAIR",
            "Suterejo Barat", "Dharmahusada Regency", "Galaxy B", "Kertajaya Indah",
            "Bundaran ITS", "PENS B", "PENS A", "ITS",
            "KONI MERR", "Galaxy A", "UNAIR A", "Airlangga Convention Center"
    };

    private int currentTargetIndex = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps3);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        busId = getIntent().getStringExtra("bus_id");
        if (busId == null) busId = "bus_2"; // default aman

        databaseRef = FirebaseDatabase
                .getInstance()
                .getReference("percobaan_5")
                .child(busId);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        for (int i = 0; i < halte.length; i++) {
            mMap.addMarker(new MarkerOptions()
                    .position(halte[i])
                    .title(halteNames[i])
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)));
        }

        List<LatLng> routePoints = new ArrayList<>();
        routePoints.add(new LatLng(-7.264030678833229, 112.7831782435129));
        routePoints.add(new LatLng(-7.268963555541420, 112.7825184200965));
        routePoints.add(new LatLng(-7.269944, 112.782269));
        routePoints.add(new LatLng(-7.270065064296550, 112.78358862129653));
        routePoints.add(new LatLng(-7.270285897855766, 112.78540984141164));
        routePoints.add(new LatLng(-7.270762152987534, 112.78905764564608));
        routePoints.add(new LatLng(-7.270837, 112.789296));
        routePoints.add(new LatLng(-7.271014, 112.789283));
        routePoints.add(new LatLng(-7.270802062610386, 112.78753146870301));
        routePoints.add(new LatLng(-7.270153, 112.782245));
        routePoints.add(new LatLng(-7.274468, 112.781863));
        routePoints.add(new LatLng(-7.275594547498979, 112.78158033583237));
        routePoints.add(new LatLng(-7.277339, 112.781021));
        routePoints.add(new LatLng(-7.280433, 112.781030));
        routePoints.add(new LatLng(-7.279258, 112.789785));
        routePoints.add(new LatLng(-7.279190, 112.789925));
        routePoints.add(new LatLng(-7.278994, 112.790085));
        routePoints.add(new LatLng(-7.276734, 112.789889));
        routePoints.add(new LatLng(-7.276160, 112.790348));
        routePoints.add(new LatLng(-7.2755785729777545, 112.79316892447837));
        routePoints.add(new LatLng(-7.274514, 112.797498));
        routePoints.add(new LatLng(-7.274294, 112.797764));
        routePoints.add(new LatLng(-7.274338, 112.798001));
        routePoints.add(new LatLng(-7.274587, 112.798090));
        routePoints.add(new LatLng(-7.274780, 112.797944));
        routePoints.add(new LatLng(-7.274762, 112.797634));
        routePoints.add(new LatLng(-7.275714181181243, 112.79338849910401));
        routePoints.add(new LatLng(-7.276494, 112.790501));
        routePoints.add(new LatLng(-7.276909, 112.790292));
        routePoints.add(new LatLng(-7.278798, 112.790427));
        routePoints.add(new LatLng(-7.279049, 112.790864));
        routePoints.add(new LatLng(-7.279449, 112.790746));
        routePoints.add(new LatLng(-7.279647, 112.790416));
        routePoints.add(new LatLng(-7.279490820849154, 112.79000086948435));
        routePoints.add(new LatLng(-7.279523, 112.789688));
        routePoints.add(new LatLng(-7.280690, 112.781024));
        routePoints.add(new LatLng(-7.280672, 112.780730));
        routePoints.add(new LatLng(-7.279352649012284, 112.78073999536396));
        routePoints.add(new LatLng(-7.277427, 112.780742));
        routePoints.add(new LatLng(-7.2762640, 112.7810240));
        routePoints.add(new LatLng(-7.274683, 112.781504));
        routePoints.add(new LatLng(-7.268991785364545, 112.78221540193282));
        routePoints.add(new LatLng(-7.264926221275623, 112.78279941563315));

        drawRealisticRoad(routePoints);

        enableMyLocation();
        startLocationUpdates();
    }

    private void drawRealisticRoad(List<LatLng> routePoints) {
        // 1) Outline gelap
        mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints)
                .width(18f)
                .color(Color.BLACK)
                .zIndex(1f)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .jointType(JointType.ROUND));

        // 2) Aspal utama
        mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints)
                .width(12f)
                .color(Color.parseColor("#2C2C2C"))
                .zIndex(2f)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .jointType(JointType.ROUND));

        // 3) Marka putus-putus di tengah
        mMap.addPolyline(new PolylineOptions()
                .addAll(routePoints)
                .width(4f)
                .color(Color.WHITE)
                .pattern(Arrays.asList(new Dash(20), new Gap(20)))
                .zIndex(3f)
                .startCap(new RoundCap())
                .endCap(new RoundCap())
                .jointType(JointType.ROUND));
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        mMap.setMyLocationEnabled(true);
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        gpsStartTime = System.currentTimeMillis();
        lastLocation = null;
        lastTime = 0;

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location location = result.getLastLocation();
                if (location == null || location.getAccuracy() > 20) return;

                if (!initialTargetSet) {
                    currentTargetIndex = findNextHalteIndex(location);
                    initialTargetSet = true;
                }

                long now = System.currentTimeMillis();
                float speedKmh = 0f;

                if (location.hasSpeed()) {
                    speedKmh = location.getSpeed() * 3.6f;
                } else if (lastLocation != null) {
                    float dist = location.distanceTo(lastLocation);
                    float dt = (now - lastTime) / 1000f;
                    if (dt > 0) speedKmh = (dist / dt) * 3.6f;
                }

                if (now - gpsStartTime < GPS_WARMUP_MS) {
                    speedKmh = 0f;
                }

                float bearing = getStableBearing(lastLocation, location, speedKmh);

                lastLocation = location;
                lastTime = now;

                updateMarkerAndCamera(location, bearing);

                // ===== CEK CHECKPOINT BUNDARAN ITS =====
                Location checkpointLoc = new Location("checkpoint");
                checkpointLoc.setLatitude(checkpoint1011.latitude);
                checkpointLoc.setLongitude(checkpoint1011.longitude);

                float distCheckpoint = location.distanceTo(checkpointLoc);

                if (distCheckpoint <= CHECKPOINT_RADIUS) {
                    passedCheckpoint1011 = true;
                }

                // ===== WAJIB: HITUNG ULANG TARGET & JARAK =====
                LatLng target = halte[currentTargetIndex];
                // ===== CEGAH LANGSUNG HALTE 10 -> 11 =====
                if (currentTargetIndex == 10 && !passedCheckpoint1011) {
                    return;
                }
                float distanceToTarget = location.distanceTo(
                        new Location("target") {{
                            setLatitude(target.latitude);
                            setLongitude(target.longitude);
                        }}
                );

                // =====================================================
                // ===== ARRIVAL LOGIC (20–30 m) =====
                if (distanceToTarget <= HALTE_RADIUS) {

                    // Kirim arrival = 1 HANYA SEKALI
                    if (!hasArrived) {
                        sendToFirebase(location, speedKmh, distanceToTarget, bearing, 1);
                        hasArrived = true;

                        // Geser target ke halte berikutnya
                        if (currentTargetIndex < halte.length - 1) {

                            if(currentTargetIndex == 10){
                                passedCheckpoint1011 = false;
                            }

                            currentTargetIndex++;
                        }
                    }

                } else {
                    // Sudah menjauh → reset arrival
                    if (hasArrived) {
                        hasArrived = false;
                    }

                    sendToFirebase(location, speedKmh, distanceToTarget, bearing, 0);
                }

            }
        };

        fusedLocationClient.requestLocationUpdates(request, locationCallback, null);
    }

    private int findNextHalteIndex(Location userLoc) {
        float minDistance = Float.MAX_VALUE;
        int nearestIndex = 0;

        for (int i = 0; i < halte.length; i++) {
            Location h = new Location("halte");
            h.setLatitude(halte[i].latitude);
            h.setLongitude(halte[i].longitude);
            float d = userLoc.distanceTo(h);
            if (d < minDistance) {
                minDistance = d;
                nearestIndex = i;
            }
        }
        // 🔥 halte terdekat = ASAL
        // 🔥 target = halte berikutnya
        if (nearestIndex < halte.length - 1) {
            return nearestIndex + 1;
        } else {
            return nearestIndex; // sudah halte terakhir
        }
    }

    private void updateMarkerAndCamera(Location loc, float bearing) {
        LatLng pos = new LatLng(loc.getLatitude(), loc.getLongitude());

        if (userMarker == null) {
            userMarker = mMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("buskuning")
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.bus1))
                    .anchor(0.5f, 0.5f)
                    .rotation(bearing)
                    .flat(true));
        } else {
            userMarker.setPosition(pos);
            userMarker.setRotation(bearing);
        }

        if (!firstZoomDone) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f));
            firstZoomDone = true;
        }
    }

    private float getStableBearing(Location last, Location current, float speedKmh) {
        if (last == null) return 0f;
        float distance = last.distanceTo(current);
        if (speedKmh > MIN_SPEED_KMH && distance > MIN_DISTANCE_METER) {
            return last.bearingTo(current);
        }
        return 0f;
    }

    private void sendToFirebase(Location loc, float speed, float distance, float bearing, int arrival) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        DatabaseReference ref = databaseRef.push();

        ref.child("time").setValue(time);
        ref.child("latitude_rt").setValue(loc.getLatitude());
        ref.child("longitude_rt").setValue(loc.getLongitude());
        ref.child("accuracy").setValue(loc.getAccuracy());
        ref.child("speed").setValue(speed);
        ref.child("bearing").setValue(bearing);
        ref.child("distance_to_target").setValue(distance);

        int halteAsalIndex = Math.max(0, currentTargetIndex - 1);
        int halteTujuanIndex = Math.min(halte.length - 1, currentTargetIndex);

        ref.child("halte_asal_id").setValue(halteAsalIndex + 1);
        ref.child("halte_tujuan_id").setValue(halteTujuanIndex + 1);


        // ===== LATITUDE & LONGITUDE TUJUAN (DITAMBAHKAN) =====
        LatLng target = halte[currentTargetIndex];
        ref.child("lat_htujuan").setValue(target.latitude);
        ref.child("lon_htujuan").setValue(target.longitude);
        // ===================================================

        ref.child("arrival").setValue(arrival);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fusedLocationClient != null && locationCallback != null)
            fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMap != null) startLocationUpdates();
    }
}