/*
 * Copyright (C) 2014 Michell Bak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miz.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.miz.apis.tmdb.TmdbApi;
import com.miz.apis.tmdb.TmdbApiService;
import com.miz.apis.tmdb.models.TmdbConfiguration;
import com.miz.functions.MizLib;
import com.miz.identification.MovieIdentification;
import com.miz.identification.MovieStructure;
import com.miz.mizuu.R;
import com.miz.utils.LocalBroadcastUtils;

import java.io.IOException;
import java.util.ArrayList;

public class IdentifyMovieService extends IntentService {

	private boolean mDebugging = true;
	private String mLanguage, mFilepath;
	private int mMovieId, mOldMovieId;
	private ArrayList<MovieStructure> mFiles = new ArrayList<>();
	private final int NOTIFICATION_ID = 4500;
	private NotificationManager mNotificationManager;
	private NotificationCompat.Builder mBuilder;

	public IdentifyMovieService() {
		super("IdentifyMovieService");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		log("onDestroy()");

		if (mNotificationManager == null)
			mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mNotificationManager.cancel(NOTIFICATION_ID);

		LocalBroadcastUtils.updateMovieLibrary(this);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		if (MizLib.isMovieLibraryBeingUpdated(this)) {
			Handler mHandler = new Handler(Looper.getMainLooper());
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(IdentifyMovieService.this, R.string.cant_identify_while_updating, Toast.LENGTH_LONG).show();
				}
			});

			return;
		}

		log("clear()");
		clear();

		log("setup()");
		setup();

		log("Intent extras");
		Bundle b = intent.getExtras();
		mMovieId = b.getInt("movieId");
		mLanguage = b.getString("language", "en");
		mFilepath = b.getString("filepath");
		mOldMovieId = b.getInt("currentMovieId");

		log("setupList()");
		setupList();

		log("start()");
		start();
	}

	private void setupList() {
		mFiles.add(new MovieStructure(mFilepath));
	}

	private void start() {
		TmdbApiService service = TmdbApi.getInstance();
		try {
			TmdbConfiguration config = service.getConfiguration(MizLib.getTmdbApiKey(this)).execute().body();
			MizLib.setTmdbImageBaseUrl(this, config.getImages().getBaseUrl());
			MovieIdentification identification = new MovieIdentification(this, null, mFiles, config);
			identification.setMovieId(Integer.valueOf(mMovieId));
			identification.setCurrentMovieId(mOldMovieId);
			identification.setLanguage(mLanguage);
			identification.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Clear all instance variables
	 * since this is an {@link IntentService}.
	 */
	private void clear() {
		mMovieId = 0;
		mLanguage = "";
		mFiles = new ArrayList<>();
	}

	private void setup() {
		// Setup up notification
		mBuilder = new NotificationCompat.Builder(getApplicationContext());
		mBuilder.setColor(getResources().getColor(R.color.color_primary));
		mBuilder.setSmallIcon(R.drawable.ic_sync_white_24dp);
		mBuilder.setTicker(getString(R.string.identifying_movie));
		mBuilder.setContentTitle(getString(R.string.identifying_movie));
		mBuilder.setContentText(getString(R.string.gettingReady));
		mBuilder.setOngoing(true);
		mBuilder.setOnlyAlertOnce(true);

		// Build notification
		Notification updateNotification = mBuilder.build();

		// Show the notification
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(NOTIFICATION_ID, updateNotification);

		// Tell the system that this is an ongoing notification, so it shouldn't be killed
		startForeground(NOTIFICATION_ID, updateNotification);
	}

	private void log(String msg) {
		if (mDebugging)
			Log.d("IdentifyMovieService", msg);
	}
}