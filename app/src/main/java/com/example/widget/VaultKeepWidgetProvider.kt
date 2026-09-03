/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 VaultKeep Contributors
 */

package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R

/**
 * VaultKeep Home Screen Widget.
 *
 * Security Guarantee:
 * This widget NEVER renders passwords, TOTP codes, or unencrypted secrets.
 * It provides secure shortcuts to trigger unlock and entry creation through the authenticated app container.
 */
class VaultKeepWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_vaultkeep)

            // Intent to open Main Activity
            val unlockIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val unlockPendingIntent = PendingIntent.getActivity(
                context,
                0,
                unlockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action_unlock, unlockPendingIntent)

            // Intent to open Add Entry via deep link
            val addIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vaultkeep://action/add")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addPendingIntent = PendingIntent.getActivity(
                context,
                1,
                addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_action_add, addPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
