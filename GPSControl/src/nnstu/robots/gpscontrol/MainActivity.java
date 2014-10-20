package nnstu.robots.gpscontrol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

public class MainActivity extends Activity {

	private LocationManager locationManager;
	private GoogleMap map;
	private Button start_button;
	private LatLng endPoint;
	private boolean paintedPath;
	private ArrayList<LatLng> points;
	private boolean firstLocation;
	private TextView textHello;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		paintedPath = false;
		firstLocation = false;
		
		textHello = (TextView) findViewById(R.id.text_hello);
		
		// Getting Google Play availability status
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());
 
        if(status!=ConnectionResult.SUCCESS){ // Google Play Services are not available
 
            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();
            return;
        }
		
		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
		//locationManager.addGpsStatusListener(this);
		
		map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();
		map.getUiSettings().setCompassEnabled(true);
		map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
			@Override
			public void onMapClick(LatLng point) {
				if (paintedPath == true)
					return;
				map.clear();
				endPoint = point;
				map.addMarker(new MarkerOptions().position(point));
			}
		});		
		
		start_button = (Button) findViewById(R.id.start_button);
		//start_button.setEnabled(false);
		start_button.setText(R.string.start_nav);
		start_button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (endPoint == null) {
					Toast toast = Toast.makeText(getApplicationContext(), 
							"Please, select the endpoint!", Toast.LENGTH_SHORT); 
					toast.show(); 
					return;
				}
				if (paintedPath == true) {
					paintedPath = false;
					map.clear();
					endPoint = null;
					start_button.setText(R.string.start_nav);
					return;
				}
				paintedPath = true;
				start_button.setText(R.string.stop_nav);
				
				drawRoute(map.getMyLocation());
			}
		});
	}
	
	private void drawRoute(Location location) {
		// Getting URL to the Google Directions API
    	String url = getDirectionsUrl(new LatLng(location.getLatitude(), 
    			location.getLongitude()), endPoint);				
		
		DownloadTask downloadTask = new DownloadTask();
		
		map.clear();
		map.addMarker(new MarkerOptions().position(endPoint));
		
		// Start downloading json data from Google Directions API
		downloadTask.execute(url);
		getGoogleBearing(location);
	}
	
	private String getDirectionsUrl(LatLng origin,LatLng dest){
		
		// Origin of route
		String str_origin = "origin="+origin.latitude+","+origin.longitude;
		
		// Destination of route
		String str_dest = "destination="+dest.latitude+","+dest.longitude;			
					
		// Sensor enabled
		String sensor = "sensor=false";			
					
		// Building the parameters to the web service
		String parameters = str_origin+"&"+str_dest+"&"+sensor;
					
		// Output format
		String output = "json";
		
		// Building the url to the web service
		String url = "https://maps.googleapis.com/maps/api/directions/"+output+"?"+parameters;		
		
		return url;
	}
	
	/** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException{
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try{
                URL url = new URL(strUrl);

                // Creating an http connection to communicate with url 
                urlConnection = (HttpURLConnection) url.openConnection();

                // Connecting to url 
                urlConnection.connect();

                // Reading data from url 
                iStream = urlConnection.getInputStream();

                BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

                StringBuffer sb  = new StringBuffer();

                String line = "";
                while( ( line = br.readLine())  != null){
                        sb.append(line);
                }
                
                data = sb.toString();

                br.close();

        }catch(Exception e){
                Log.d("Exception while downloading url", e.toString());
        }finally{
                iStream.close();
                urlConnection.disconnect();
        }
        return data;
     }
	
    /** A class to download data from Google Directions URL */
	private class DownloadTask extends AsyncTask<String, Void, String>{			
				
		// Downloading data in non-ui thread
		@Override
		protected String doInBackground(String... url) {
				
			// For storing data from web service
			String data = "";
					
			try{
				// Fetching the data from web service
				data = downloadUrl(url[0]);
			}catch(Exception e){
				Log.d("Background Task",e.toString());
			}
			return data;		
		}
		
		// Executes in UI thread, after the execution of
		// doInBackground()
		@Override
		protected void onPostExecute(String result) {			
			super.onPostExecute(result);			
			
			ParserTask parserTask = new ParserTask();
			
			// Invokes the thread for parsing the JSON data
			parserTask.execute(result);
				
		}		
	}
	
	/** A class to parse the Google Directions in JSON format */
    private class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>> >{
    	
    	// Parsing the data in non-ui thread    	
		@Override
		protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
			
			JSONObject jObject;	
			List<List<HashMap<String, String>>> routes = null;			           
            
            try{
            	jObject = new JSONObject(jsonData[0]);
            	DirectionsParser parser = new DirectionsParser();
            	
            	// Starts parsing data
            	routes = parser.parse(jObject);    
            }catch(Exception e){
            	e.printStackTrace();
            }
            return routes;
		}
		
		// Executes in UI thread, after the parsing process
		@Override
		protected void onPostExecute(List<List<HashMap<String, String>>> result) {
			points = null;
			PolylineOptions lineOptions = null;
			
			// Traversing through all the routes
			for(int i=0;i<result.size();i++){
				points = new ArrayList<LatLng>();
				lineOptions = new PolylineOptions();
				
				// Fetching i-th route
				List<HashMap<String, String>> path = result.get(i);
				
				// Fetching all the points in i-th route
				for(int j=0; j < path.size(); j++){
					HashMap<String,String> point = path.get(j);					
					
					double lat = Double.parseDouble(point.get("lat"));
					double lng = Double.parseDouble(point.get("lng"));
					LatLng position = new LatLng(lat, lng);	
					
					points.add(position);						
				}
				
				// Adding all the points in the route to LineOptions
				lineOptions.addAll(points);
				lineOptions.width(5);
				lineOptions.color(Color.BLUE);	
				
			}
			
			// Drawing polyline in the Google Map for the i-th route
			map.addPolyline(lineOptions);							
		}			
    }

	@Override
	protected void onResume() {
		super.onResume();
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000 * 10, 10, locationListener);
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
			switch (status) {
				case LocationProvider.OUT_OF_SERVICE:
					start_button.setEnabled(false);
					break;
				case LocationProvider.TEMPORARILY_UNAVAILABLE:
					start_button.setEnabled(false);
					break;
				case LocationProvider.AVAILABLE:
					start_button.setEnabled(true);
					break;
			}
		}
	};
	
	private float getGoogleBearing(Location location) {
		if (location == null || points == null)
			return -1;
		Location nextLoc = new Location("");
		nextLoc.setLatitude(points.get(1).latitude);
		nextLoc.setLongitude(points.get(1).longitude);
		textHello.setText(String.valueOf(location.bearingTo(nextLoc)));
		return location.bearingTo(nextLoc);
	}
	
	private void showLocation(Location location) {
		if (location == null)
			return;
		
		LatLng my_location = new LatLng(location.getLatitude(), location.getLongitude());
		
        map.setMyLocationEnabled(true);
        if (!firstLocation) {
        	map.moveCamera(CameraUpdateFactory.newLatLngZoom(my_location, 13));
        	firstLocation = true;
        }
        
        if (paintedPath == true) {
        	drawRoute(location);
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
