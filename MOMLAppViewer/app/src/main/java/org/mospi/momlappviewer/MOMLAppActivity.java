package org.mospi.momlappviewer;

import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import org.mospi.moml.framework.pub.core.MOMLFragmentActivity;
import org.mospi.moml.framework.pub.core.MOMLLogHandler;
import org.mospi.momlappviewer.MainActivity.OpenType;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class MOMLAppActivity extends MOMLFragmentActivity {
	
	String openType = "";
	String url = "";
	
	private String getAbsDirUrl(String url) 
	{
		String lowerCaseUrl = url.toLowerCase(Locale.US);
		
		if (lowerCaseUrl.endsWith(".xml") || lowerCaseUrl.endsWith(".htm") || lowerCaseUrl.endsWith(".html"))
			return url;
		
		if (url.endsWith("/"))
			return url;
		
		final String finalFullUrl = url + "/";
		final Object syncObject = new Object();
		final boolean isConnectWithDirName[] = new boolean[1]; 

		synchronized (syncObject) {
			new Thread(new Runnable() {
				@Override
				public void run() {

					synchronized (syncObject) {
						try {
							URL url = new URL(finalFullUrl);
							HttpURLConnection connection = (HttpURLConnection) url.openConnection();
							connection.setConnectTimeout(5000);
							connection.connect();
							if (connection.getResponseCode() == HttpURLConnection.HTTP_OK)
								isConnectWithDirName[0] = true;
							connection.disconnect();
						} catch (Exception e) {
							e.printStackTrace();
						}
						syncObject.notify();
					}
				}
			}).start();
			try {
				syncObject.wait();
			} catch (InterruptedException e) {
			}
		}
		
		if (isConnectWithDirName[0])
			return finalFullUrl;
		
		return url;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        
        url = getIntent().getExtras().getString("url");
        url = getAbsDirUrl(url);
        openType = getIntent().getExtras().getString("openType");
        
        if (openType.equals(OpenType.APPLICATION)) {
       		loadApplication(url);
        } else if (openType.equals(OpenType.MOMLUI)) {
        	loadUrl(url);
        } else if (openType.equals(OpenType.HTML)) {
        	getMomlView().getRoot().runScript("userVariable.url='" + url+"'");
        	loadUrl("embed:/webView2.xml");
        } 
		
	}
	private static final int MENUID_REFRESH = 1; 
	private static final int MENUID_CONSOLE = 2; 
	private static final int MENUID_QUIT = 3; 
	public boolean onCreateOptionsMenu(Menu menu) {

		menu.add(Menu.NONE, MENUID_REFRESH, Menu.NONE, "Refresh");
		menu.add(Menu.NONE, MENUID_CONSOLE, Menu.NONE, "Dev Console");
		menu.add(Menu.NONE, MENUID_QUIT, Menu.NONE, "Quit");
		
		return super.onCreateOptionsMenu(menu);
		
	}
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == MENUID_REFRESH) {
	        if (openType.equals(OpenType.APPLICATION))
	        	loadApplication(url);
	        else if (openType.equals(OpenType.MOMLUI))
	        	loadUrl(url);
			else if (openType.equals(OpenType.HTML))
				getMomlView().getRoot().runScript("root.webView.refresh");
	        initLog();
		} else if (itemId == MENUID_CONSOLE) {
			toggleLog();
		} else if (itemId == MENUID_QUIT) {
			finish();
		}
		return super.onOptionsItemSelected(item);
	}
	
	private boolean hasCheckedApplicationLog;
	private LogView logView;
	private void initLog() {
		if (logView != null) {
			LogView.clearLogs();
			checkApplicationLogFilter();
		}
	}
	private void toggleLog() {
		if (logView != null) {
			logView.destroy();
			logView = null;
			return;
		}
		MOMLLogHandler.setOnLogListenser(LogView.momlLogListener);
		
		logView = new LogView(getMomlView()) {
			@Override
			protected void onClose() {
				super.onClose();
				logView = null;
			}
		};
		logView.show();
		
		if (!hasCheckedApplicationLog) {
			hasCheckedApplicationLog = true;
			LogView.clearLogs();
			checkApplicationLogFilter();
		}
	}
	
	private void checkApplicationLogFilter() {
		try {
			Object application = getMomlView().getRootContainer().getMomlContext().getObjectManager().findObject("application");
			Method m = application.getClass().getMethod("getLogFilter", (Class[])null);
			
			String filter = (String)m.invoke(application, (Object[])null);
			if (filter == null || filter.length() == 0 || filter.equals("none")) {
				m = application.getClass().getMethod("setLogFilter", new Class[] { String.class });
				m.invoke(application, "all");
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
