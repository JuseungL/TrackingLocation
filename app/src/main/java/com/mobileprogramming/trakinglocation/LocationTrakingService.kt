package com.mobileprogramming.trakinglocation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class LocationTrackingService : Service() {
    val db = Firebase.firestore
    val realtimeDb = FirebaseDatabase.getInstance().getReference()

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var canTrack = false

    val CHANNEL_ID = "location_tracking_service"

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        // Firebase Table name
        val locCollection = db.collection("TB_LOC")

        val channel = NotificationChannel(CHANNEL_ID, "Location Tracking", NotificationManager.IMPORTANCE_DEFAULT)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 여기에 펫 id 입력
        val petId = "4Jipcx2xHXmvcKNVc6cO"

        realtimeDb.child("PetLocation").child(petId).child("canTrack").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                canTrack = dataSnapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(databaseError: DatabaseError) {
                canTrack = false
            }
        })

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!canTrack) return

                locationResult.lastLocation?.let { location ->
                    val latitude = location.latitude  // 위도
                    val longitude = location.longitude  // 경도
                    val geoPointValue = GeoPoint(latitude, longitude)

                    // Firestore에 저장할 필드 이름 (예: "202311072301")
                    val documentName = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault()).format(Calendar.getInstance().time)

                    val locationData = HashMap<String, Any>()
                    locationData["lat"] = latitude
                    locationData["lng"] = longitude
                    locationData["geoPoint"] = geoPointValue // 거리 계산 혹시나 필요할까봐 추가함

                    val data = HashMap<String, Any>()
                    data[documentName] = locationData

                    val documentReference = locCollection.document(petId)
                    documentReference.update(data)
                        .addOnSuccessListener {
                            Log.d("Check", "Firestore save well")
                        }
                        .addOnFailureListener { }

                    // Realtime Database에 저장
                    realtimeDb.child("PetLocation").child(petId).child("lat").setValue(latitude)
                    realtimeDb.child("PetLocation").child(petId).child("lng").setValue(longitude)
                    realtimeDb.child("PetLocation").child(petId).child("geoPoint").setValue(geoPointValue)
                        .addOnSuccessListener {
                            Log.d("Check", "Realtime DB save well")
                        }
                        .addOnFailureListener { e ->
                            Log.e("LocationTrackingService", "Realtime DB save failed: ${e.message}")}
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking Service")
            .setContentText("Tracking location...")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .build()

        startForeground(1, notification)

        val locationRequest = LocationRequest.create().apply {
            interval = 1 * 1000;
            fastestInterval = 1 * 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
