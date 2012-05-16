package com.test.twitterclient;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.Twitter;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Activity will allow user to search tweets
 * By default a number of 10 tweets is loaded
 * User is able to load 10 more tweets and so on
 * 
 * @author Andrei Sobolev
 *
 */
public class TwitterSearch extends Activity	{
	
	private List<twitter4j.Tweet> list;
	private Map<String, Drawable> imageList = new HashMap<String, Drawable>();
	private Button search, stop;
	private EditText searchQuery;
	private ProgressBar progressSearchTweets;
	private AsyncTask<String, Void, String> task;
	private TwittAdapter adapter;
	private Twitter twitter;
	private AccessToken accessToken;
	private ListView searchResult;
	private ConfigurationBuilder cb;
	private Query query;
	private QueryResult result;
	private static final String ACCESS_TOKEN_FILENAME = "access.token";
	private static final int MAX_TWEETS_COUNT = 10;
	private static int PAGE = 1;
	
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
						// Hide soft keyboard (if not, than dialog will not be visible on the screen, but will be "shown")
						InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(searchQuery.getWindowToken(), 0);
						NotifyUserInternetIsOff();
						stop.setVisibility(8);
						search.setVisibility(0);
						progressSearchTweets.setVisibility(8);
					}
				}
			}
		}
	};
	
	
	@Override
	public void onCreate(Bundle savedInstanceState)	{
		super.onCreate(savedInstanceState);
		
		// Hide application title bar
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		// Register broadcast receiver to receive internet connection status updates.
        registerReceiver(br, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		
		setContentView(R.layout.search);
	    
		// Load access token from file storage
	    FileInputStream fis;
		try {
			fis = openFileInput(ACCESS_TOKEN_FILENAME);
			ObjectInputStream ois = new ObjectInputStream(fis);
			accessToken = (AccessToken) ois.readObject();
			ois.close();
			fis.close();
		} catch (Exception e) {
		}
		
		// Build configuration for correct twitter instance
		cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		  .setOAuthConsumerKey(CONSUMER_KEY)
		  .setOAuthConsumerSecret(CONSUMER_SECRET)
		  .setOAuthAccessToken(accessToken.getToken())
		  .setOAuthAccessTokenSecret(accessToken.getTokenSecret());
		
		// Initiate twitter instance with needed configuration
		twitter = new TwitterFactory(cb.build()).getInstance();
	    
		progressSearchTweets = (ProgressBar) this.findViewById(R.id.progress_search);
		
		searchQuery = (EditText) this.findViewById(R.id.search_text);
		
		search = (Button) this.findViewById(R.id.search_tweets);
		search.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SearchTwitts();
			}
		});
		
		stop = (Button) this.findViewById(R.id.stop_search);
		stop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				StopSearch();
			}
		});
		
		list = new LinkedList<twitter4j.Tweet>();
		searchResult = (ListView) this.findViewById(R.id.result);
		adapter = new TwittAdapter(getApplicationContext(), R.layout.twitt_row, list);
		searchResult.setAdapter(adapter);
		adapter.notifyDataSetChanged();
		
		searchResult.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position,
					long resource_id) {
				// If last item in list is pressed ("Show more tweets") more tweets is loaded
				if(position + 1 == list.size())	{
					ProgressBar progressLoadMore = (ProgressBar) view.findViewById(R.id.progress_load_more);
					progressLoadMore.setVisibility(0);
					TextView text = (TextView) view.findViewById(R.id.load_more);
					text.setVisibility(8);
					LoadMoreSearchResults();
				}
			}
		});
	}
	
    /**
     * Broadcast receiver must be unregistered from this activity.
     */
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	unregisterReceiver(br);
    }
	
	// Start tweets search when user presses "Search button"
	private void SearchTwitts()	{
		if(searchQuery.getText().toString().length() > 0)	{
			list.clear();
			searchResult.setVisibility(8);
			stop.setVisibility(0);
			search.setVisibility(8);
			progressSearchTweets.setVisibility(0);
			query = new Query(searchQuery.getText().toString()); 	// Create search query
			query.setPage(PAGE); 									// Set page number to show
			query.setRpp(MAX_TWEETS_COUNT);							// Set number of tweets per page
			task = new DownloadTask().execute("list");
		}
	}
	
	// Stop search when user presses "Stop" button
	private void StopSearch()	{
		if(task.getStatus() == Status.PENDING || task.getStatus() == Status.RUNNING)	{
			task.cancel(true);
			list.clear();
			searchResult.setVisibility(8);
			stop.setVisibility(8);
			search.setVisibility(0);
			progressSearchTweets.setVisibility(8);
		}
	}
	
	// Load more tweets
	private void LoadMoreSearchResults()	{
		adapter.removeLastRow();
		task = new DownloadTask().execute("list");
	}
	
	/**
	 * Notify user that internet connection is off
	 */
    protected void NotifyUserInternetIsOff()	{
    	AlertDialog alertDialog = new AlertDialog.Builder(
				TwitterSearch.this).create();
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
     * DownloadTask class. It makes possible to load data from internet asynchronously.
     * Parameters are passed as a String Array.
     * 
     * @author Andrei Sobolev
     *
     */
	public class DownloadTask extends AsyncTask<String, Void, String>	{
		
		List<twitter4j.Tweet> tmp = new LinkedList<twitter4j.Tweet>();

		// Executed when method "execute" is run on task
		@Override
		protected String doInBackground(String... arg0) {
			
			// Load a needed page of 10 tweets
			if(arg0[0].equalsIgnoreCase("list"))	{				
				try {
					
					result = twitter.search(query);
					tmp.addAll(result.getTweets());				
		        } catch (Exception e) {
		        	return "error";
		        }
		        
		        // Download user image.
		        // Images are stored in HashMap
		        // Images are loaded once per user
				for(int i = 0; i < tmp.size(); i++)	{
					if(!imageList.containsKey(tmp.get(i).getProfileImageUrl()))	{
						HttpParams httpParameters = new BasicHttpParams();
				        HttpConnectionParams.setConnectionTimeout(httpParameters, 3000);	    	
					    HttpConnectionParams.setSoTimeout(httpParameters, 5000);
					    HttpClient client = new DefaultHttpClient(httpParameters); 
					    HttpGet request = new HttpGet(tmp.get(i).getProfileImageUrl());
			    	    HttpResponse response;
			    	    try {
							response = client.execute(request);
				    		Drawable img = Drawable.createFromStream(response.getEntity().getContent(), "src");
				    		imageList.put(tmp.get(i).getFromUser(), img);
						} catch (Exception e) {
						}
					}	
				}
				return "list";
			}	else	{
				return "error";
			}
		}

		@Override
		protected void onPostExecute(String responce)	{
			if(responce.equalsIgnoreCase("list"))	{
				addItemsToList(tmp);  // Data must be added in a parent thread
				progressSearchTweets.setVisibility(8);
				searchResult.setVisibility(0);
				stop.setVisibility(8);
				search.setVisibility(0);
				PAGE += 1;
			}	else	{
				progressSearchTweets.setVisibility(8);
				adapter.notifyDataSetChanged();
				stop.setVisibility(8);
				search.setVisibility(0);
			}
		}
	}
	
	/**
	 * Adding data to list of tweets
	 * @param tmp - twitter4j.Status type input
	 */
	protected void addItemsToList(List<twitter4j.Tweet> tmp)	{
		list.addAll(tmp);
		if(tmp.size() > 0)	{
			adapter.addLoadMoreItem();
		}
		adapter.notifyDataSetChanged();  // Notify that data set changed, ListView adapter will refresh view
	}

	/**
	 * Adapter to show custom ListView
	 * @author Andrei Sobolev
	 *
	 */
	public class TwittAdapter extends ArrayAdapter<twitter4j.Tweet>	{
		
		private Context context;
		private List<twitter4j.Tweet> list;
		private static final int GENERAL_VIEW = 0;
		private static final int LAST_ROW = 1;
		private static final int VIEW_COUNT = 2;
		private TreeSet<Integer> lastItem = new TreeSet<Integer>();

		public TwittAdapter(Context context, int textViewResourceId, List<twitter4j.Tweet> list)	{
			super(context, textViewResourceId, list);
			this.context = context;
			this.list = list;
		}
		
		@Override
        public int getItemViewType(int position) {
			return lastItem.contains(position) ? LAST_ROW : GENERAL_VIEW;
        }
 
        @Override
        public int getViewTypeCount() {
            return VIEW_COUNT;  // number of view type in one ListView
        }
		
		@Override
		public int getCount()	{
			return this.list.size();
		}
		
		@Override
		public twitter4j.Tweet getItem(int index) {
			return this.list.get(index);
		}
		
	    public void addLoadMoreItem() {
	            lastItem.add(list.size() - 1); // Add "Show more tweets" row to ListView
	    }
	    
	    public void removeLastRow()	{
	    	lastItem.clear(); // Remove "Show more tweets" row from ListView
	    }
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			
			LayoutInflater inflater = null;
			
			int type = getItemViewType(position);
			switch (type) {
                case GENERAL_VIEW:
                	inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					row = inflater.inflate(R.layout.twitt_row, parent, false);
					
					ImageView userImage = (ImageView) row.findViewById(R.id.user_image);
					userImage.setBackgroundDrawable(imageList.get(list.get(position).getFromUser()));
					
					TextView fullName = (TextView) row.findViewById(R.id.user_full_name);
					fullName.setVisibility(8);
					
					TextView username = (TextView) row.findViewById(R.id.username);
					username.setText("@" + getItem(position).getFromUser());
					
					TextView date = (TextView) row.findViewById(R.id.date);
					date.setText(CountDaysFromPostingATweet(getItem(position).getCreatedAt()));
					
					TextView status = (TextView) row.findViewById(R.id.status_text);
					status.setText(getItem(position).getText());
					break;
                case LAST_ROW:
                	inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					row = inflater.inflate(R.layout.last_row, parent, false);
                    break;
				}
			return row;
		}
	}
	
	/**
	 * Returns a string with amount of minutes/hours/days past from tweet submission
	 */
	private String CountDaysFromPostingATweet(Date date)	{
		String value;
		long difference = Calendar.getInstance().getTimeInMillis() - date.getTime();
		if(difference > 86400000)	{
			value = "~" + (int)(difference/86400000) + " day";
			if((int)(difference/86400000) > 1)	{
				value += "s";
			}
		}	else	{
			if(difference > 3600000)	{
				value = "~" + (int)(difference/3600000) + " h";
			}	else	{
				value = "~" + (int)(difference/60000) + " min";
			}
		}		
		return value;
	}
}