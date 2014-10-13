package nnstu.robots.gpscontrol;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;

public class MainActivity extends Activity {

	private LocationManager locationManager;
	private GoogleMap map;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		
		map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();
		
		map.getUiSettings().setCompassEnabled(true);
	}

	@Override
	protected void onResume() {
		super.onResume();
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 10, 10, locationListener);
	    /*locationManager.requestLocationUpdates(
	        LocationManager.NETWORK_PROVIDER, 1000 * 10, 10,
	        locationListener);*/
		checkEnabled();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		locationManager.removeUpdates(locationListener);
	}
	
	private LocationListener locationListener = new LocationListener() {
		
		@Override
		public void onLocationChanged(Location location) {
			showLocation(location);
		}
		
		@Override
		public void onProviderDisabled(String provider) {
			checkEnabled();
		}
		
		@Override
		public void onProviderEnabled(String provider) {
			checkEnabled();
			showLocation(locationManager.getLastKnownLocation(provider));
		}
		
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			/*
			 * http://stackoverflow.com/questions/14222152/androids-onstatuschanged-not-working
			 * 
			 * 
			 * Called when the provider status changes. This method is called when 
			 * a provider is unable to fetch a location or if the provider has recently 
			 * become available after a period of unavailability.
			 * 
			 * Parameters
			 * 
			 * provider		the name of the location provider associated with this update.
			 * status		OUT_OF_SERVICE if the provider is out of service, and this is not expected 
			 * to change in the near future; TEMPORARILY_UNAVAILABLE if the provider is temporarily 
			 * unavailable but is expected to be available shortly; and AVAILABLE if the provider is 
			 * currently available.
			 * extras		an optional Bundle which will contain provider specific status variables.
			 * 
			 * A number of common key/value pairs for the extras Bundle are listed below. Providers that 
			 * use any of the keys on this list must provide the corresponding value as described below.
			 * 
			 * satellites - the number of satellites used to derive the fix
			 */
			/*if (provider.equals(LocationManager.GPS_PROVIDER)) {
				//tvStatusGPS.setText("Status: " + String.valueOf(status));
			} else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
				//tvStatusNet.setText("Status: " + String.valueOf(status));
			}*/
		}
	};
	
	private void showLocation(Location location) {
		if (location == null)
			return;
		
		LatLng my_location = new LatLng(location.getLatitude(), location.getLongitude());

        map.setMyLocationEnabled(true);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(my_location, 13));

        /*map.addMarker(new MarkerOptions()
                .title("You")
                .position(my_location));*/
		if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
			//tvLocationGPS.setText(formatLocation(location));
		} else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
			//tvLocationNet.setText(formatLocation(location));
		}
	}
	
	private void checkEnabled() {
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.gps_disabled_text)
			       .setTitle(R.string.gps_disabled_title)
			       .setCancelable(false)
			       .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
					}
				});
			AlertDialog dialog = builder.create();
			dialog.show();
		}
		//tvEnabledNet.setText("Enabled: " + locationManager
		//		.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
	}
	
	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	*/
}
