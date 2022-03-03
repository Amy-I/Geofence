package com.example.geofence;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationRequest;
import android.os.Bundle;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.geofence.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.lang.Math;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    int FINE_LOCATION_ACCESS_REQUEST_CODE = 10001;

    private GoogleMap mMap;
    // private Location mLocationRequest;

    private ActivityMapsBinding binding;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private GeofencingClient geofencingClient;

    // Store the points for the Geofence Polygon
    private List<LatLng> latLngList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        geofencingClient = LocationServices.getGeofencingClient(this);
    }

    // Manipulates the map once available.
    @SuppressLint("MissingPermission")
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Turn off 3D map
        mMap.setBuildingsEnabled(false);

        // Zoom controls
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);

        // 5 - landmass/continent
        mMap.setMinZoomPreference(5);

        // 20 - buildings
        float initialZoom = 20;

        enableUserLocation();

        // If location is enabled
        if(mMap.isMyLocationEnabled()) {
            // Zoom to the current location
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this,
                    new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng current_location = new LatLng(location.getLatitude(), location.getLongitude());
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current_location, initialZoom));
                            }
                        }
                    }
            );
        }

        mMap.setOnMapLongClickListener(this);

    }

    // Check for permission as needed
    @SuppressLint("MissingPermission")
    private void enableUserLocation(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED){
            mMap.setMyLocationEnabled(true);
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



    @Override
    public void onMapLongClick(@NonNull LatLng latLng) {
        addPolyMarker(latLng);

        // Complex shapes will be ineffective, polygon should have 4 sides
        if(latLngList.size() == 4){
            mMap.clear();
            // Sort latLngList
            // If latLngList isn't sorted, polygon will be drawn incorrectly
            sortLatLngClockwise(latLngList);
            addPolygon(latLngList);
            latLngList.clear();
        }
    }

    private void addPolyMarker(LatLng latLng){
        MarkerOptions markerOptions = new MarkerOptions().position(latLng);
        mMap.addMarker(markerOptions);
        latLngList.add(latLng);
    }

    private void addPolygon(List<LatLng> latLngs){
        PolygonOptions polygonOptions = new PolygonOptions();
        polygonOptions.strokeColor(Color.argb(225, 0, 0, 225));
        polygonOptions.fillColor(Color.argb(65, 0, 0, 225));
        polygonOptions.strokeWidth(4);
        polygonOptions.addAll(latLngList);
        mMap.addPolygon(polygonOptions);
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

        LatLng cLatLng = new LatLng(cLatitude/latLngList.size(), cLongitude/latLngList.size());

        return cLatLng;
    }

    private double findAngle(LatLng center, LatLng point){
        double angle = Math.atan((point.latitude - center.latitude) / (point.longitude - center.longitude));
        return angle;
    }
}