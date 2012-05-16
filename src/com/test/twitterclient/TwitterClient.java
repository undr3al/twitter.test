package com.test.twitterclient;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Applications main screen.
 * From this activity user can authorize, browse his timeline, 
 * make a search and post tweets
 * 
 * @author Andrei Sobolev
 *
 */
public class TwitterClient extends Activity {
	
	private Button login, logout, timeline, search, tweet, clear;
	private EditText tweetText;
	private AsyncTask<String, Void, String> task;
	private String unauthURL, authURL;
	private Twitter twitter = null;
	private RequestToken requestToken = null;
	private AccessToken accessToken = null;
	private User user = null;
	private Bitmap userImage = null;
	private ImageView userImageField = null; // View where user image (userImage) is placed.
	private TextView username = null;
	private ProgressBar progress_login, progress_post_tweet;
	private SharedPreferences sharedPrefs;
	private static String PREFERENCE_FILE_NAME = "twitter";
	private static String USER_IMAGE_FILENAME = "user_image";
	private static String ACCESS_TOKEN_FILENAME = "access.token";

	private static final String CONSUMER_KEY = "7uxGp9Ow5SoaFgQJADRj6A";
	private static final String CONSUMER_SECRET = "dcKDfBIn4qaNIxdu9xKT0IihEOsLgBS5glLVnSvwgI0";
	
	/**
	 * Broadcast receiver will notify application, if internet connection is turned off.
	 */
	private BroadcastReceiver br = new BroadcastReceiver()	{
		@Override
		public void onReceive(Context context, Intent intent) {
			boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
			boolean isFailover = intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);
			if(isFailover || noConnectivity)	{
				if(task != null)	{
					if(task.getStatus() == AsyncTask.Status.RUNNING || task.getStatus() == AsyncTask.Status.PENDING)	{
						task.cancel(true);
						NotifyUserInternetIsOff();
					}
				}
			}
		}
	};
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	CheckPreferences();
        super.onCreate(savedInstanceState);
        
        // Hide application title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Register broadcast receiver to receive internet connection status updates.
        registerReceiver(br, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        
        setContentView(R.layout.main_screen);
        
    	username = (TextView) this.findViewById(R.id.username);
    	userImageField = (ImageView) this.findViewById(R.id.user_image);
        
        this.login = (Button) this.findViewById(R.id.login);
        this.login.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				GetUnauthorizedToken();
			}
		});
        
        this.logout = (Button) this.findViewById(R.id.logout);
        this.logout.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LogOut();
			}
		});
        
        this.timeline = (Button) this.findViewById(R.id.timeline);
        this.timeline.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ShowTimelineScreen();
			}
		});
        
        this.search = (Button) this.findViewById(R.id.search);
        this.search.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				ShowSearchTweetsScreen();
			}
		});
        
        this.tweet = (Button) this.findViewById(R.id.tweet);
        this.tweet.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				PostTweet();
			}
		});
        
        this.tweetText = (EditText) this.findViewById(R.id.twitt_text);
        this.progress_login = (ProgressBar) this.findViewById(R.id.progress_login);
        this.progress_post_tweet = (ProgressBar) this.findViewById(R.id.progress_tweet);
        
        this.clear = (Button) this.findViewById(R.id.clear);
        clear.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				tweetText.setText(""); 
			}
		});
        
    	sharedPrefs = getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE);
    	if(sharedPrefs.getBoolean("authorized", false))	{
    		
    		/*
    		 *  Set application to authorized state
    		 *  Read access token and user image from storage.
    		 *  Assign to Twitter object an authorized instance of TwitterFactory
    		 */
    		
    		try {
				FileInputStream fis = openFileInput(USER_IMAGE_FILENAME);
				userImageField.setImageBitmap(BitmapFactory.decodeStream(fis));
				fis = openFileInput(ACCESS_TOKEN_FILENAME);
				ObjectInputStream ois = new ObjectInputStream(fis);
				accessToken = (AccessToken) ois.readObject();
				username.setText(sharedPrefs.getString("username", "Please, sign in"));
				ois.close();
				fis.close();
				ConfigurationBuilder configuration = new ConfigurationBuilder();
				configuration.setDebugEnabled(true)
				  .setOAuthConsumerKey(CONSUMER_KEY)
				  .setOAuthConsumerSecret(CONSUMER_SECRET)
				  .setOAuthAccessToken(accessToken.getToken())
				  .setOAuthAccessTokenSecret(accessToken.getTokenSecret());
				twitter = new TwitterFactory(configuration.build()).getInstance();
				login.setVisibility(8);
				logout.setVisibility(0);
			} catch (Exception e) {
			}
    	}	else	{
    		
    		/*
    		 * Set application to unauthorized state
    		 * Assign Twitter object an unauthorized instance of TwitterFactory
    		 * Set only login controls to enabled state (user can do only one action - login to the system)
    		 */
    		
    		twitter = new TwitterFactory().getInstance();
    		twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
    		username.setText(sharedPrefs.getString("username", "Please, sign in"));
            this.clear.setEnabled(false);
            this.tweet.setEnabled(false);
            this.search.setEnabled(false);
            this.timeline.setEnabled(false);
            this.tweetText.setEnabled(false);
            this.tweetText.setFocusable(false);
            this.userImageField.setVisibility(4);
    	}
    }
    
    /**
     * Broadcast receiver must be unregistered from this activity.
     */
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(br);
    }
    
    /**
     * Receive result from LoginTwitter activity
     * 1 - access granted by the user, 0 - access denied.
     */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)	{
		 super.onActivityResult(requestCode, resultCode, data);
		 if(resultCode == 1)	{
			 
			 // Fetch Access Token from twitter
			 authURL = data.getExtras().getString("url");
			 GetOAuthTokenForUser(authURL);
		 }	else	{
			 
			 // Change visibility setting back to "not logged in" state.
			 login.setVisibility(0);
			 progress_login.setVisibility(8);
		 }
	}
	
	/**
	 * Notify user that internet connection is off
	 */
    protected void NotifyUserInternetIsOff()	{
    	AlertDialog alertDialog = new AlertDialog.Builder(
				TwitterClient.this).create();
		alertDialog.setTitle("No Internet");
		alertDialog
			.setMessage("Internet connection is not available.\nPlease turn on Iinternet connection.");
		alertDialog.setButton("OK",
				new DialogInterface.OnClickListener() {
			
				public void onClick(DialogInterface dialog,
						int which) {
					dialog.cancel();
				}
			});
		alertDialog.show();
    }
    
	/**
	 * Notify user that access to user account is denied
	 */
    protected void NotifyUserAccessToAccountIsDenied()	{
    	AlertDialog alertDialog = new AlertDialog.Builder(
				TwitterClient.this).create();
		alertDialog.setTitle("Denied");
		alertDialog
			.setMessage("Seems, that authorization was unsuccessful.\nPlease ensure your credentials and then try again.");
		alertDialog.setButton("OK",
				new DialogInterface.OnClickListener() {
			
				public void onClick(DialogInterface dialog,
						int which) {
					dialog.cancel();
				}
			});
		alertDialog.show();
    }
	
	/**
	 * Check if preferences exist, if not create new (creates a file with preferences, if does not exist)
	 */
	protected void CheckPreferences()	{
		sharedPrefs = getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE);
    	if(!sharedPrefs.contains("authorized"))	{
			SharedPreferences.Editor editor = sharedPrefs.edit();
			editor.putBoolean("authorized", false);
			editor.commit();
		}
	}
    
    /**
     * Log user out.
     * Delete all data (access token, user image).
     * Disable all other controls (non login)
     */
    private void LogOut()	{
    	login.setVisibility(0);
    	logout.setVisibility(8);
    	this.tweetText.setEnabled(false);
    	this.tweetText.setFocusable(false);
    	this.clear.setEnabled(false);
    	this.tweet.setEnabled(false);
    	this.timeline.setEnabled(false);
    	this.search.setEnabled(false);
    	sharedPrefs = getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putBoolean("authorized", false);
		editor.remove("username");
		editor.commit();
		getApplicationContext().deleteFile(ACCESS_TOKEN_FILENAME);
		getApplicationContext().deleteFile(USER_IMAGE_FILENAME);
		userImageField.setVisibility(4);
		twitter.setOAuthAccessToken(null);
		accessToken = null;
		if(userImage != null)	{
			userImage.recycle(); // bitmap must be recycled (or memory leaks might happen)
			userImage = null;
		}
		username.setText("Please, sign in");
    }
    
    /**
     * Create a task to retrieve RequestToken from Twitter
     */
    private void GetUnauthorizedToken()	{
    	login.setVisibility(8);
    	progress_login.setVisibility(0);
		task = new DownloadTask().execute("unauth");
    }
    
    /**
     * Launch new activity (using browser) where user 
     * gives permission for this application to access his account.
     */
    private void GetUserPermissionToAccount(String unauthURL)	{
    	Intent i = new Intent(this, LoginTwitter.class);
    	i.putExtra("url", unauthURL);
    	startActivityForResult(i, 0);
    }
    
    /**
     * Create a new task to load access token to access users account.
     */
    private void GetOAuthTokenForUser(String authURL)	{
    	this.progress_login.setVisibility(0);
    	if (authURL.contains("oauth_token")) {
    		
    		// If url contains "oauth_token" string, then the authorization is successful
    		task = new DownloadTask().execute("auth", authURL);
    	}	else	{
    		NotifyUserAccessToAccountIsDenied();
    	}
    }
    
    /**
     * AccessToken received, now user is fully authorized, 
     * enable all application controls and write data to file storage (user image and access token)
     */
    private void UserIsAuthorized()	{
    	this.login.setVisibility(8);
    	this.progress_login.setVisibility(8);
    	this.logout.setVisibility(0);
    	this.tweetText.setEnabled(true);
    	this.tweetText.setFocusable(true);
    	this.tweetText.setFocusableInTouchMode(true);
    	this.clear.setEnabled(true);
    	this.tweet.setEnabled(true);
    	this.timeline.setEnabled(true);
    	this.search.setEnabled(true);
    	this.userImageField.setImageBitmap(userImage);
    	this.userImageField.setVisibility(0);
    	this.username.setText("@" + user.getScreenName());
    	this.sharedPrefs = getSharedPreferences(PREFERENCE_FILE_NAME, MODE_PRIVATE);
		SharedPreferences.Editor editor = sharedPrefs.edit();
		editor.putBoolean("authorized", true);
		editor.putString("username", "@" + user.getScreenName());
		editor.commit();
		FileOutputStream fos;
		try {
			fos = openFileOutput(USER_IMAGE_FILENAME, Context.MODE_PRIVATE);
			userImage.compress(Bitmap.CompressFormat.PNG, 65, fos);
			fos = openFileOutput(ACCESS_TOKEN_FILENAME, Context.MODE_PRIVATE);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(accessToken);
			oos.close();
			fos.close();
		} catch (Exception e) {
		}
    }
    
    /**
     * Launch new activity to show Timeline to the user.
     */
    private void ShowTimelineScreen()	{
    	Intent i = new Intent(this, TwitterTimeline.class);
    	startActivity(i);
    }
    
    /**
     * Post tweet to Twitter
     * Hide "tweet command buttons"
     */
    private void PostTweet()	{
    	if(tweetText.getText().toString().length() > 0)	{
        	clear.setVisibility(8);
        	tweet.setVisibility(8);
        	progress_post_tweet.setVisibility(0);
    		task = new DownloadTask().execute("tweet");
    	}
    }
    
    /**
     * Tweet is posted successfully; notify user with toast message.
     * Make "tweet command buttons" visible again
     */
    private void TweetPosted()	{
    	clear.setVisibility(0);
    	tweet.setVisibility(0);
    	progress_post_tweet.setVisibility(8);
		Toast toast = Toast.makeText(getApplicationContext(), "Status updated", Toast.LENGTH_LONG);
		toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
		toast.show();
		tweetText.setText("");
    }
    
    /**
     * Launch Tweet Search activity.
     */
    private void ShowSearchTweetsScreen()	{
    	Intent i = new Intent(this, TwitterSearch.class);
    	startActivity(i);
    }
    
    /**
     * DownloadTask class. It makes possible to load data from internet asynchronously.
     * Parameters are passed as a String Array.
     * 
     * @author Andrei Sobolev
     *
     */
    private class DownloadTask extends AsyncTask<String, Void, String>	{

    	// Executed when method "execute" is run on task
		@Override
		protected String doInBackground(String... params) {
			if(params[0].equalsIgnoreCase("unauth"))	{
				
				// Download a RequestToken needed for authorization.
				unauthURL = "";
				int counter = 0;
				while(unauthURL.length() < 5 && counter < 5)	{
					try {
						if(requestToken == null)	{
						    requestToken = twitter.getOAuthRequestToken();
						}
					    unauthURL = requestToken.getAuthorizationURL();
			    	} catch (TwitterException e) {
						return "error";
					}
			    	counter += 1;
				}
				if(counter == 5)	{
					return "error"; // Problem with internet connection (internet is On, but no response is received for 5 times)
				}	else	{
					return "unauth";
				}
			}	else if(params[0].equalsIgnoreCase("auth"))	{
				
				// Download an AccessToken to access user account
				int counter = 0;
				while(accessToken == null && counter < 5)	{
					try {
						if(accessToken == null)	{
							accessToken = twitter.getOAuthAccessToken();
						}
						user = twitter.showUser(accessToken.getUserId());
		    		} catch (TwitterException e) {
						return "error";
					}
		    		counter += 1;
				}
				
				// Download user image.
	        	HttpParams httpParameters = new BasicHttpParams();
	        	HttpConnectionParams.setConnectionTimeout(httpParameters, 3000);	    	
		    	HttpConnectionParams.setSoTimeout(httpParameters, 5000);
		    	HttpClient client = new DefaultHttpClient(httpParameters); 
		        HttpGet request = new HttpGet(user.getProfileImageURL().toString());
    	        HttpResponse response;
				try {
					response = client.execute(request);
	    			userImage = ((BitmapDrawable)Drawable.createFromStream(response.getEntity().getContent(), "src")).getBitmap();
				} catch (Exception e) {
					return "error";
				}

				if(counter == 5)	{
					return "error"; // Problem with internet connection (internet is On, but no response is received for 5 times)
				}	else	{
					return "auth";
				}
			}	else if(params[0].equalsIgnoreCase("tweet"))	{
				
				// Post a tweet to Twitter
				twitter4j.Status status = null;
		    	try {
					status = twitter.updateStatus(tweetText.getText().toString());
				} catch (Exception e) {
				}
				if(status != null)	{
					if(status.getText().length() > 0)	{
						return "tweet";
					}	else	{
						return "error";  // If status text is empty, then tweet posting was unsuccessful
					}
				}	else	{
					return "error"; // If status is null, then tweet posting was unsuccessful
				}
			}	else	{
				return "error";
			}
		}
		
		
		// Executed when doInBackground method finishes work and 
		// task was not interrupted by some other method
		@Override
		protected void onPostExecute(String responce) {
			if(responce.equalsIgnoreCase("unauth"))	{
				progress_login.setVisibility(8);
				GetUserPermissionToAccount(unauthURL);
			}	else if(responce.equalsIgnoreCase("auth"))	{
				progress_login.setVisibility(8);
				UserIsAuthorized();
			}	else if(responce.equalsIgnoreCase("tweet"))	{
				TweetPosted();
			}	else	{
				progress_login.setVisibility(8);
				if(accessToken == null)	{
					login.setVisibility(0);
				}	else	{
					logout.setVisibility(0);
				}
				clear.setVisibility(0);
		    	tweet.setVisibility(0);
		    	progress_post_tweet.setVisibility(8);
				NotifyUserInternetIsOff();
			}
		}
    }
}