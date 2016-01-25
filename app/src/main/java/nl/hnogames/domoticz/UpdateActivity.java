/*
 * Copyright (C) 2015 Domoticz
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package nl.hnogames.domoticz;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import nl.hnogames.domoticz.Domoticz.Domoticz;
import nl.hnogames.domoticz.Interfaces.UpdateDomoticzServerReceiver;
import nl.hnogames.domoticz.Interfaces.UpdateDownloadReadyReceiver;
import nl.hnogames.domoticz.Interfaces.UpdateVersionReceiver;
import nl.hnogames.domoticz.Interfaces.VersionReceiver;
import nl.hnogames.domoticz.Utils.SharedPrefUtil;
import nl.hnogames.domoticz.Utils.UsefulBits;

public class UpdateActivity extends AppCompatActivity {

    @SuppressWarnings("FieldCanBeLocal")
    private final int SERVER_UPDATE_TIME = 2;                       // Time in minutes
    @SuppressWarnings("unused")
    private String TAG = UpdateActivity.class.getSimpleName();

    private SharedPrefUtil mSharedPrefs;
    private Domoticz mDomoticz;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private MaterialDialog progressDialog;
    private Button buttonUpdateServer;
    private TextView currentServerVersionValue;
    private TextView updateServerVersionValue;
    private TextView updateSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        mSharedPrefs = new SharedPrefUtil(this);
        mDomoticz = new Domoticz(this);

        initViews();
    }

    private void initViews() {

        currentServerVersionValue = (TextView) findViewById(R.id.currentServerVersion_value);
        updateServerVersionValue = (TextView) findViewById(R.id.updateServerVersion_value);
        updateSummary = (TextView) findViewById(R.id.updateSummary);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshData();
            }
        });

        currentServerVersionValue.setText(mSharedPrefs.getServerVersion());

        if (mSharedPrefs.isServerUpdateAvailable()) {
            updateSummary.setText(R.string.server_update_available);
            updateServerVersionValue.setText(mSharedPrefs.getUpdateVersionAvailable());
        } else if (mDomoticz.isDebugEnabled()) {
            String message = "Debugging: " + getString(R.string.server_update_available);
            updateSummary.setText(message);
        } else
            updateSummary.setText(R.string.server_update_not_available);

        buttonUpdateServer = (Button) findViewById(R.id.buttonUpdateServer);
        buttonUpdateServer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showServerUpdateWarningDialog();
            }
        });
        if (!mSharedPrefs.isServerUpdateAvailable() && !mDomoticz.isDebugEnabled())
            buttonUpdateServer.setEnabled(false);
    }

    private void refreshData() {
        checkServerUpdateVersion();
        getCurrentServerVersion();
    }

    private void showServerUpdateWarningDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.server_update)
                .content(getString(R.string.update_server_warning)
                        + UsefulBits.newLine()
                        + UsefulBits.newLine()
                        + getString(R.string.continue_question))
                .positiveText(R.string.yes)
                .negativeText(R.string.no)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        checkForUpdatePrerequisites();
                    }
                })
                .show();
    }

    private void checkForUpdatePrerequisites() {

        MaterialDialog.Builder mdb = new MaterialDialog.Builder(this);
        mdb.title(R.string.msg_please_wait)
                .content(R.string.please_wait_while_we_check)
                .progress(true, 0);
        progressDialog = mdb.build();
        progressDialog.show();

        mDomoticz.getUpdateDownloadReady(new UpdateDownloadReadyReceiver() {
            @Override
            public void onUpdateDownloadReady(boolean downloadOk) {
                if (downloadOk || mDomoticz.isDebugEnabled()) updateServer();
                else {
                    progressDialog.cancel();
                    showMessageUpdateNotReady();
                }
            }

            @Override
            public void onError(Exception error) {
                progressDialog.cancel();
                String message = String.format(
                        getString(R.string.error_couldNotCheckForConfig),
                        mDomoticz.getErrorMessage(error));
                showSimpleSnackbar(message);
            }
        });
    }

    private void showMessageUpdateNotReady() {
        String title =getString(R.string.server_update_not_ready);
        String message = getString(R.string.update_server_warning) + UsefulBits.newLine()
                + getString(R.string.update_server_downloadNotReady2);
        showSimpleDialog(title, message);
    }

    private void updateServer() {
        // Cancel the check prerequisites dialog
        progressDialog.cancel();

        final boolean showMinMax = false;
        final MaterialDialog dialog = new MaterialDialog.Builder(this)
                .title(R.string.msg_please_wait)
                .content(getString(R.string.please_wait_while_server_updated) + UsefulBits.newLine()
                    + getString(R.string.this_take_minutes))
                .cancelable(false)
                .progress(false, SERVER_UPDATE_TIME * 60, showMinMax)
                .show();

        CountDownTimer mCountDownTimer = new CountDownTimer(SERVER_UPDATE_TIME * 60 * 1000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                dialog.incrementProgress(1);
            }

            @Override
            public void onFinish() {
                dialog.cancel();
                refreshData();
            }
        };

        mCountDownTimer.start();
        if (!mDomoticz.isDebugEnabled() || mSharedPrefs.isServerUpdateAvailable()) {
            mDomoticz.updateDomoticzServer(new UpdateDomoticzServerReceiver() {
                @Override
                public void onUpdateFinish(boolean updateSuccess) {
                    if (!updateSuccess) showMessageUpdateFailed();
                    else showMessageUpdateSuccess();
                }

                @Override
                public void onError(Exception error) {
                    showMessageUpdateNotStarted();
                }
            });
        }
    }

    private void showMessageUpdateSuccess() {
        String message = "Update was successful";
        String title = "Update successful";
        showSimpleDialog(title, message);
    }

    private void showMessageUpdateFailed() {
        String message = "Update failed. Please login to your server and/or review the logs there";
        String title = "Update failed";
        showSimpleDialog(title, message);
    }

    private void showMessageUpdateNotStarted() {
        String message = getString(R.string.update_not_started_unknown_error);
        String title = getString(R.string.update_not_started);
        showSimpleDialog(title, message);
    }

    private void checkServerUpdateVersion() {
        mSwipeRefreshLayout.setRefreshing(true);

        // Get latest Domoticz version update
        mDomoticz.getUpdate(new UpdateVersionReceiver() {
            @Override
            public void onReceiveUpdate(String version, boolean haveUpdate) {
                // Write update version to shared preferences
                mSharedPrefs.setUpdateVersionAvailable(version);
                mSharedPrefs.setServerUpdateAvailable(haveUpdate);
                if (!mDomoticz.isDebugEnabled()) buttonUpdateServer.setEnabled(haveUpdate);

                if (haveUpdate) {
                    updateServerVersionValue.setText(version);
                    updateSummary.setText(R.string.server_update_available);
                }
                else updateSummary.setText(R.string.server_update_not_available);

                mSwipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onError(Exception error) {
                String message = String.format(
                        getString(R.string.error_couldNotCheckForUpdates),
                        mDomoticz.getErrorMessage(error));
                showSimpleSnackbar(message);
                mSharedPrefs.setUpdateVersionAvailable("");
                updateServerVersionValue.setText(R.string.not_available);

                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void getCurrentServerVersion() {
        mSwipeRefreshLayout.setRefreshing(true);

        // Get latest Domoticz server version
        mDomoticz.getServerVersion(new VersionReceiver() {
            @Override
            public void onReceiveVersion(String serverVersion) {
                mSwipeRefreshLayout.setRefreshing(false);

                if (!UsefulBits.isEmpty(serverVersion)) {


                    mSharedPrefs.setServerVersion(serverVersion);
                    currentServerVersionValue.setText(serverVersion);
                } else currentServerVersionValue.setText(R.string.not_available);
            }

            @Override
            public void onError(Exception error) {
                mSwipeRefreshLayout.setRefreshing(false);

                String message = String.format(
                        getString(R.string.error_couldNotCheckForUpdates),
                        mDomoticz.getErrorMessage(error));
                showSimpleSnackbar(message);
                mSharedPrefs.setServerVersion("");
                currentServerVersionValue.setText(R.string.not_available);
            }
        });
    }

    private void showSimpleSnackbar(String message) {
        CoordinatorLayout fragmentCoordinatorLayout =
                (CoordinatorLayout) findViewById(R.id.coordinatorLayout);
        Snackbar.make(fragmentCoordinatorLayout, message, Snackbar.LENGTH_SHORT).show();
    }

    private void showSimpleDialog(String title, String message) {
        new MaterialDialog.Builder(this)
                .title(title)
                .content(message)
                .positiveText(R.string.ok)
                .show();
    }
}