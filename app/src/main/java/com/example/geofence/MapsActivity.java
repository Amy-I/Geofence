package com.example.geofence;

import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyProtection;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.geofence.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.maps.android.PolyUtil;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.android.SphericalUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.lang.Math;
import java.util.Map;

public class MapsActivity extends DrawerBaseActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    // Used for location permissions
    private boolean mLocationPermissionsGranted = false;
    private static final int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;

    private GoogleMap mMap;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference databaseReference, geofenceReference;

    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private GeofencingClient geofencingClient;

    // Store the points for the Geofence Polygon
    private List<LatLng> latLngList = new ArrayList<>();

    // Also store markers and polygons in array to be cleared separately
    private List<Marker> markerList = new ArrayList<>();
    private List<Polygon> polygonList = new ArrayList<>();
    private List<Polygon> polygonToAdd = new ArrayList<>();
    private boolean hasPolyBeenDrawn = false;
    private boolean isPetSafe = false;

    // Pet location
    LatLng pLoc;
    // Marker pMarker;
    private Map<String, Marker> pMarkerMap = new HashMap<>();

    // Notifications
    public static final String CHANNEL_ID = "channel_1";
    public boolean notifHasBeenSent = false;

    // Buttons
    Button bAdd_Safe_Area;
    Button bConfirm;
    Button bDelete;
    Button bCancel;

    // Locking map in Hybrid mode
    private MutableLiveData<Boolean> isMapModeLocked = new MutableLiveData<Boolean>();

    // Global Variable for user logged in
    UserApplication userApplication = (UserApplication) this.getApplication();
    String mUID;

    // Pet Name and Tracker ID list
    List<Pet> petNameTracker;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setNavActivityTitle("Map");

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mUID = userApplication.getmUserID();

        // Locked map mode
        isMapModeLocked.setValue(false);

        // Pet tracker
        petNameTracker = new ArrayList<Pet>();

        // Clients
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);

        // Get permissions
        enableUserLocation();

        // Notifications
        createNotificationChannel();

        // Foreground Service
        Intent intent = new Intent(this, ForegroundService.class);
        startForegroundService(intent);

        // Buttons
        bAdd_Safe_Area = (Button) findViewById(R.id.Add_Safe_Area);
        bConfirm = (Button) findViewById(R.id.Confirm);
        bDelete = (Button) findViewById(R.id.Delete);
        bCancel = (Button) findViewById(R.id.Cancel);
    }

    // Manipulates the map once available.
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // To handle Marker zoom
        LatLngBounds.Builder builder = new LatLngBounds.Builder();

        // Custom map style
        /*try {

            boolean success = googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                            this, R.raw.map_styles
                    )
            );
            if(success){
                Log.i("Yo", "Style applied");
            }

            if(!success){
                Log.e("Yo", "Style parsing failed");
            }
        } catch (Resources.NotFoundException e){
            Log.e("Yo", "Can't find style, ", e);
        }*/

        // Turn off 3D map
        mMap.setBuildingsEnabled(false);

        // Zoom controls
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);

        // 5 - landmass/continent
        mMap.setMinZoomPreference(5);

        // 15 - street
        float initialZoom = 17;

        // Get permissions
        enableUserLocation();

        // Initialize Firebase database
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference();
        geofenceReference = firebaseDatabase.getReference("Users/"+ mUID + "/Geofences");

        addPolygonsFromDatabase();

        // If location is enabled
        if(mLocationPermissionsGranted) {

            mMap.setMyLocationEnabled(true);

            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this,
                    new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {

                            if (location != null) {
                                // Zoom to the current location
                                LatLng current_location = new LatLng(location.getLatitude(), location.getLongitude());
                                builder.include(current_location);
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current_location, initialZoom));

//                                LatLngBounds bounds = builder.build();
//                                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 500);
//                                mMap.moveCamera(cameraUpdate);

                                // Write to database
                                // databaseReference.setValue(current_location);
                            }
                        }
                    }
            );

            // Write data to database based on location listener
//            LocationListener locationListener = new LocationListener() {
//                @Override
//                public void onLocationChanged(@NonNull Location location) {
//                    if (location != null) {
//                        LatLng current_location = new LatLng(location.getLatitude(), location.getLongitude());
//
//                        // Move to location
//                        mMap.moveCamera(CameraUpdateFactory.newLatLng(current_location));
//
//                        // Write to database
//                        // databaseReference.setValue(current_location);
//                    }
//                }
//            };

            // LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            // locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            //locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

            // Make a list for Pet name and Tracker ID
            databaseReference.child("Users").child(mUID).child("Pets").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()){

                        String petName = dataSnapshot.child("petName").getValue(String.class);
                        String petTID = dataSnapshot.child("petTrackerID").getValue(String.class);

                        Pet pet = new Pet(petName, petTID, "000");
                        Log.i("Yo", pet.getPetTrackerID());

                        petNameTracker.add(pet);
                        Log.i("Yo", "Pet Name Tracker: " + petNameTracker.get(0));
                    }

                    for (Pet pet : petNameTracker) {
                        // Read from database (pet location)
                        databaseReference.child("Trackers").child(pet.getPetTrackerID()).addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {

                                // Reading
                                if (snapshot != null && snapshot.child("isActive").getValue(Boolean.class)) {
                                    pLoc = new LatLng(
                                            snapshot.child("latitude").getValue(Double.class),
                                            snapshot.child("longitude").getValue(Double.class)
                                    );

                                    Log.i("Yo", String.valueOf(pLoc));

                                    //final LatLng latLng = pLoc;

                                    Marker previousMarker = pMarkerMap.get(pet.getPetName());

                                    // Update position
                                    if (previousMarker != null){
                                        previousMarker.setPosition(pLoc);
                                    }
                                    // Create new one
                                    else{
                                        Marker pMarker = mMap.addMarker(new MarkerOptions().position(pLoc).title(pet.getPetName() + " is here!"));
                                        // pMarker.showInfoWindow();
                                        pMarkerMap.put(pet.getPetName(), pMarker);
                                    }

//                                    if (pMarker != null) {
//                                        pMarker.remove();
//                                        pMarker = null;
//                                    }
//
//                                    pMarker = mMap.addMarker(new MarkerOptions().position(pLoc).title(petNameTracker.get(finalI).getPetName() + " is here!"));
//                                    pMarker.showInfoWindow();
//
//                                    builder.include(pMarker.getPosition());

//                                    LatLngBounds bounds = builder.build();
//                                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 500);
//                                    mMap.moveCamera(cameraUpdate);

                                    // Check if the pet is inside the geofence
                                    if (polygonList.size() != 0 && pLoc != null) {
                                        // Only send notif if pet is outside area and notif has not been sent already
                                        // This is done to avoid spamming everytime pet moves

                                        if (!isPetInArea(pLoc) && !notifHasBeenSent) {
                                            // Send notification
                                            Log.i("Yo", pet.getPetName() + " is out of bounds!");

                                            NotificationCompat.Builder builder = new NotificationCompat.Builder(MapsActivity.this, CHANNEL_ID)
                                                    .setContentTitle("Pet Outside Safe Area!")
                                                    .setContentText("Your pet, " + pet.getPetName() + ", has left the safe area.")
                                                    .setSmallIcon(R.drawable.ic_launcher_background)
                                                    .setPriority(Notification.PRIORITY_MAX);

                                            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(MapsActivity.this);
                                            notificationManagerCompat.notify(0, builder.build());

                                            notifHasBeenSent = true;
                                        }

                                        // Reset the notification when pet re-enters geofence
                                        else if (isPetInArea(pLoc)) {
                                            Log.i("Yo", "Pet is safe :)");
                                            notifHasBeenSent = false;
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {

                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {

                }
            });

            Log.i("Yo", petNameTracker.toString());

        }

        // Change Map Type based on Zoom
        mMap.setOnCameraMoveListener(new GoogleMap.OnCameraMoveListener() {
            @Override
            public void onCameraMove() {
                if(!isMapModeLocked.getValue()) {
                    changeMapTypeZoom();
                }
            }
        });

        // Change Map Type when Zoom doesn't changed but Map is no longer locked
        isMapModeLocked.observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if(!isMapModeLocked.getValue()){
                    changeMapTypeZoom();
                }
            }
        });

        // Add UI for Geofence //
        bAdd_Safe_Area.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bAdd_Safe_Area.setVisibility(View.INVISIBLE);
                bConfirm.setVisibility(View.VISIBLE);
                bConfirm.setEnabled(false);
                bDelete.setVisibility(View.VISIBLE);
                bDelete.setEnabled(false);
                bCancel.setVisibility(View.VISIBLE);
                isMapModeLocked.setValue(true);
                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                mMap.setOnMapLongClickListener(MapsActivity.this);
            }
        });

        bConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearPolyMarkers();
                polygonList.addAll(polygonToAdd);
                for (Polygon polygon : polygonToAdd){
                    geofenceReference.push().setValue(polygon);
                }
                //polygonToAdd.clear();
                clearPolygons(polygonToAdd);
                hasPolyBeenDrawn = false;
                bConfirm.setVisibility(View.INVISIBLE);
                bDelete.setVisibility(View.INVISIBLE);
                bCancel.setVisibility(View.INVISIBLE);
                bAdd_Safe_Area.setVisibility(View.VISIBLE);
                isMapModeLocked.setValue(false);
                mMap.setOnMapLongClickListener(null);
            }
        });

        // Click Polygon to delete
        bDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearPolyMarkers();
                deleteAPolygon(polygonToAdd);
                //isMapModeLocked = false;
                //bDelete.setEnabled(false);
                //bConfirm.setEnabled(false);
            }
        });

        bCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clearPolyMarkers();
                clearPolygons(polygonToAdd);
                hasPolyBeenDrawn = false;
                bConfirm.setVisibility(View.INVISIBLE);
                bDelete.setVisibility(View.INVISIBLE);
                bCancel.setVisibility(View.INVISIBLE);
                bAdd_Safe_Area.setVisibility(View.VISIBLE);
                isMapModeLocked.setValue(false);
                mMap.setOnMapLongClickListener(null);
            }
        });


    }

    // Disable Back button navigation
    @Override
    public void onBackPressed() {

    }

    // Check for permission as needed
    @SuppressLint("MissingPermission")
    private void enableUserLocation(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED){
            //mMap.setMyLocationEnabled(true);
            mLocationPermissionsGranted = true;
        }
        else{
            // Ask for permission
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                // Let user know why the access is needed, then ask

                /* Add UI */

                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        FINE_LOCATION_ACCESS_REQUEST_CODE);
            }
            else{
                // Directly ask for permission
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        FINE_LOCATION_ACCESS_REQUEST_CODE);
            }
        }

    }

    // To be called after getting location permissions
    private void initMap(){
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mLocationPermissionsGranted = false;

        switch (requestCode) {
            case FINE_LOCATION_ACCESS_REQUEST_CODE: {
                if (grantResults.length > 0) {
                    for (int i = 0; i < grantResults.length; i++) {
                        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                            mLocationPermissionsGranted = false;
                            Log.d("Yo", "onRequestPermissionsResult: permission failed");
                            return;
                        }
                    }
                    Log.d("Yo", "onRequestPermissionsResult: permission granted");
                    mLocationPermissionsGranted = true;
                    //initialize our map
                    initMap();
                }
            }
        }
    }

    // Creating the notification channel
    private void createNotificationChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "channel";
            String description = "channel description";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * This onMapLongClick function will be adjusted to be used in
     * an "editor" state for geofencing in the future. For now, there
     * is no UI for entering the "editor" state as I'm trying to get
     * the basic functionality down.
     */

    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {
        addPolyMarker(latLng);

        // Complex shapes will be ineffective, polygon should have 4 sides
        if(latLngList.size() == 4){
            // Clear markers
            clearPolyMarkers();
            // Sort latLngList
            // If latLngList isn't sorted, polygon will be drawn incorrectly
            sortLatLngClockwise(latLngList);
            addPolygon(latLngList);
            latLngList.clear();
        }

        if(hasPolyBeenDrawn == true){
            bConfirm.setEnabled(true);
            bDelete.setEnabled(true);
            hasPolyBeenDrawn = false;
        }
    }

    private void addPolyMarker(LatLng latLng){
        MarkerOptions markerOptions = new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = mMap.addMarker(markerOptions);
        markerList.add(marker);
        latLngList.add(latLng);
    }

    private void clearPolyMarkers(){
        // Remove all Polygon Markers from map
        for (Marker marker : markerList){
            marker.remove();
        }

        // Clear all items in list
        markerList.clear();
    }

    private void addPolygonsFromDatabase(){
        geofenceReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                clearPolygons(polygonList);

                for (DataSnapshot dataSnapshot : snapshot.getChildren()){
                    PolygonOptions polygonOptions = new PolygonOptions();
                    int strokeColor = dataSnapshot.child("strokeColor").getValue(Integer.class);
                    int fillColor = dataSnapshot.child("fillColor").getValue(Integer.class);
                    int strokeWidth = dataSnapshot.child("strokeWidth").getValue(Integer.class);
                    LatLng latlng0 = new LatLng(
                            dataSnapshot.child("points").child("0").child("latitude").getValue(Double.class),
                            dataSnapshot.child("points").child("0").child("longitude").getValue(Double.class));
                    LatLng latlng1 = new LatLng(
                            dataSnapshot.child("points").child("1").child("latitude").getValue(Double.class),
                            dataSnapshot.child("points").child("1").child("longitude").getValue(Double.class));
                    LatLng latlng2 = new LatLng(
                            dataSnapshot.child("points").child("2").child("latitude").getValue(Double.class),
                            dataSnapshot.child("points").child("2").child("longitude").getValue(Double.class));
                    LatLng latlng3 = new LatLng(
                            dataSnapshot.child("points").child("3").child("latitude").getValue(Double.class),
                            dataSnapshot.child("points").child("3").child("longitude").getValue(Double.class));

                    List<LatLng> latlngdb = new ArrayList<>();
                    latlngdb.add(latlng0);
                    latlngdb.add(latlng1);
                    latlngdb.add(latlng2);
                    latlngdb.add(latlng3);

                    polygonOptions.strokeColor(strokeColor);
                    polygonOptions.fillColor(fillColor);
                    polygonOptions.strokeWidth(strokeWidth);
                    polygonOptions.addAll(latlngdb);

                    Polygon polygon = mMap.addPolygon(polygonOptions);
                    polygonList.add(polygon);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        //notifHasBeenSent = true;
    }

    private void addPolygon(List<LatLng> latLngs){
        Double sizeOfPolygon = squareMetersToSquareFeet(SphericalUtil.computeArea(latLngs));
        if(sizeOfPolygon < 100 || sizeOfPolygon > 36000){
            Log.i("Yo", sizeOfPolygon.toString());
            Toast.makeText(MapsActivity.this, "Safe Area must be between\n100 and 36000 sqft", Toast.LENGTH_SHORT).show();
        }
        else{
            PolygonOptions polygonOptions = new PolygonOptions();
            polygonOptions.strokeColor(Color.argb(225, 0, 0, 225));
            polygonOptions.fillColor(Color.argb(65, 0, 0, 225));
            polygonOptions.strokeWidth(4);
            polygonOptions.addAll(latLngs);
            Polygon polygon = mMap.addPolygon(polygonOptions);

            polygonToAdd.add(polygon);
            hasPolyBeenDrawn = true;
        }
    }

    private void deleteAPolygon(List<Polygon> pList){
        // Pop off one polygon, delete one by one
        if(!pList.isEmpty()){
            Polygon pRemove = pList.get(pList.size() - 1);
            pRemove.remove();

            pList.remove(pList.size() - 1);
        }

        if(pList.isEmpty()){
            bDelete.setEnabled(false);
            bConfirm.setEnabled(false);
        }
    }

    private void clearPolygons(List<Polygon> pList){
        // Remove all polygons from map
        for(Polygon polygon : pList){
            polygon.remove();
        }

        pList.clear();
    }

    private void sortLatLngClockwise(List<LatLng> latLngs){
        // Calculate center point
        LatLng center = findCenterPoint(latLngs);

        // Sort by angles
        for (int i = 0; i < latLngs.size()-1; i++){
            for(int j = 1; j < latLngs.size(); j++){
                if(findAngle(center, latLngs.get(j)) < findAngle(center, latLngs.get(i))){
                    LatLng temp = latLngs.get(i);
                    latLngs.set(i, latLngs.get(j));
                    latLngs.set(j, temp);
                }
            }
        }

    }

    private LatLng findCenterPoint(List<LatLng> latLngs){
        double cLatitude = 0;
        double cLongitude = 0;

        for (LatLng latlng:latLngList){
            cLatitude += latlng.latitude;
            cLongitude += latlng.longitude;
        }

        return new LatLng(cLatitude/ (double) latLngs.size(), cLongitude/ (double) latLngs.size());
    }

    private double findAngle(LatLng center, LatLng point){
        return Math.atan((point.latitude - center.latitude) / (point.longitude - center.longitude));
    }

    private boolean isPetInArea(LatLng latlng){
        boolean bool = false;
        for (Polygon polygon : polygonList){
            if(PolyUtil.containsLocation(latlng, polygon.getPoints(), false)){
                return true;
            }
        }
        return false;
    }

    private void changeMapTypeZoom(){
        if (mMap.getCameraPosition().zoom >= 19){
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        }
        else{
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
    }

    private double squareMetersToSquareFeet(double meters){
        return meters * 10.7639;
    }

}