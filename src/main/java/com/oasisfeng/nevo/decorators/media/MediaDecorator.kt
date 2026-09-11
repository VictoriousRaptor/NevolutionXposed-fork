package com.oasisfeng.nevo.decorators.media

import android.app.Notification
import android.app.Notification.Action
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.View
import android.widget.RemoteViews

import java.lang.reflect.Method

import com.oasisfeng.nevo.xposed.compat.PackageHookContext
import com.oasisfeng.nevo.xposed.compat.XC_MethodHook
import com.oasisfeng.nevo.xposed.compat.XposedBridge
import com.oasisfeng.nevo.xposed.compat.XposedHelpers

import com.oasisfeng.nevo.sdk.Decorating
import com.oasisfeng.nevo.sdk.HookSupport
import com.oasisfeng.nevo.sdk.NevoDecoratorService
import com.oasisfeng.nevo.xposed.BuildConfig
import com.oasisfeng.nevo.xposed.R


/**
* Created by Deng on 2019/2/18.
*/
class MediaDecorator : NevoDecoratorService() {
	override fun createSystemUIDecorator() : NevoDecoratorService.SystemUIDecorator {
		/** not working */
		return object : NevoDecoratorService.SystemUIDecorator(this.prefKey), HookSupport {
			override fun hook(loadPackageParam: PackageHookContext) {
				val remotable = XposedHelpers.findClass("android.view.RemotableViewMethod", loadPackageParam.classLoader)
				val target = XposedHelpers.findMethodExact(View::class.java, "setOutlineAmbientShadowColor", Int::class.java)
				val isAnnotationPresent = XposedHelpers.findMethodBestMatch(Method::class.java, "isAnnotationPresent", Class::class.java)
				XposedBridge.hookMethod(isAnnotationPresent, object : XC_MethodHook() {
					override fun beforeHookedMethod(param: MethodHookParam) {
						if (param.thisObject !is Method) return
						if (param.thisObject != target) return
						val annotation = param.args[0] as? Class<*>
						if (annotation != remotable) return
						param.setResult(true)
					}
				})
				val getAnnotation = XposedHelpers.findMethodBestMatch(Method::class.java, "getAnnotation", Class::class.java)
				XposedBridge.hookMethod(getAnnotation, object : XC_MethodHook() {
					override fun beforeHookedMethod(param: MethodHookParam) {
						if (param.thisObject !is Method) return
						if (param.thisObject != target) return
						val annotation = param.args[0] as? Class<*>
						if (annotation != remotable) return
						param.setResult(XposedHelpers.newInstance(remotable))
					}
				})
			}

			override fun onNotificationPosted(sbn:StatusBarNotification):Decorating {
				val n = sbn.getNotification()
				if (!isMediaNotification(n)) {
					// Log.d(TAG, "not media notification");
					return Decorating.Unprocessed
				}
				val extras = n.extras
				val title = extras.getCharSequence(Notification.EXTRA_TITLE, null)
				val text = extras.getCharSequence(Notification.EXTRA_TEXT, null)
				val backgroundColor:Int
				val textColor:Int
				if (n.getLargeIcon() != null) {
					val bitmap = getLargeIcon(n)
					val colors = ColorUtil.getColor(bitmap)
					backgroundColor = colors[0]
					textColor = colors[1]
				} else {
					backgroundColor = Color.BLACK
					textColor = Color.WHITE
				}
				val packageName = sbn.getPackageName()
				val context = getPackageContext()
				val pm = context.getPackageManager()
				var appLabel:String
				try {
					appLabel = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
				} catch (ex:PackageManager.NameNotFoundException) {
					appLabel = "??"
				}
				val appLabel0 = appLabel
				val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
				val selectableItemBackground = typedArray.getResourceId(0, 0)
				typedArray.recycle()
				val bindRemoteViews = { remoteViews: RemoteViews ->
						remoteViews.setTextViewText(R.id.appName, appLabel0)
						if (title != null)
							remoteViews.setTextViewText(R.id.title, title)
						if (text != null)
							remoteViews.setTextViewText(R.id.subtitle, text)
						remoteViews.setImageViewIcon(R.id.smallIcon, n.getSmallIcon())
						remoteViews.setTextColor(R.id.appName, textColor)
						remoteViews.setTextColor(R.id.title, textColor)
						remoteViews.setTextColor(R.id.subtitle, textColor)
						remoteViews.setImageViewIcon(R.id.largeIcon, n.getLargeIcon())
						remoteViews.setInt(R.id.smallIcon, "setColorFilter", textColor)
						remoteViews.setInt(R.id.foregroundImage, "setColorFilter", backgroundColor)
						remoteViews.setInt(R.id.background, "setBackgroundColor", backgroundColor)
						// remoteViews.setInt(R.id.title, "setOutlineAmbientShadowColor", Color.RED)
						// remoteViews.setInt(R.id.subtitle, "setOutlineAmbientShadowColor", Color.RED)
					}
				@Suppress("DEPRECATION")
				try {
					val target = context.createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
					val bindAction = { remoteViews: RemoteViews, id: Int, action: Action ->
							// Log.d(TAG, id + " " + action.getIcon() + " " + n.getSmallIcon());
							remoteViews.setViewVisibility(id, View.VISIBLE)
							// remoteViews.setImageViewBitmap(id, BitmapFactory.decodeResource(context.getResources(),action.getIcon().getResId()));
							val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
								Icon.createWithResource(target, action.getIcon().resId)
							else
								Icon.createWithResource(target, action.icon)
							remoteViews.setImageViewIcon(id, icon)
							remoteViews.setOnClickPendingIntent(id, action.actionIntent)
							remoteViews.setInt(id, "setColorFilter", textColor)
							remoteViews.setInt(id, "setBackgroundResource", selectableItemBackground) }
					if (NevoDecoratorService.LocalDecorator.overridedContentView(n) == null) {
						n.contentView = NevoDecoratorService.LocalDecorator.overrideContentView(n, RemoteViews(BuildConfig.APPLICATION_ID, R.layout.media_notifition_layout))
					}
					bindRemoteViews(n.contentView)
					val compacts = n.extras.getIntArray(Notification.EXTRA_COMPACT_ACTIONS)
					if (n.actions != null && compacts != null) {
						// Log.d(TAG, "" + n.actions.size)
						if (n.actions.size > 0)
							bindAction(n.contentView, R.id.ic_0, n.actions[compacts[0]])
						if (n.actions.size > 1)
							bindAction(n.contentView, R.id.ic_1, n.actions[compacts[1]])
						if (n.actions.size > 2)
							bindAction(n.contentView, R.id.ic_2, n.actions[compacts[2]])
					} else {
						Log.d(TAG, "no action")
					}
					if (NevoDecoratorService.LocalDecorator.overridedBigContentView(n) == null) {
						n.bigContentView = NevoDecoratorService.LocalDecorator.overrideBigContentView(n, RemoteViews(BuildConfig.APPLICATION_ID, R.layout.media_notifition_layout_big))
					}
					bindRemoteViews(n.bigContentView)
					if (n.actions != null) {
						// Log.d(TAG, "" + n.actions.size)
						if (n.actions.size > 0)
							bindAction(n.bigContentView, R.id.ic_0, n.actions[0])
						if (n.actions.size > 1)
							bindAction(n.bigContentView, R.id.ic_1, n.actions[1])
						if (n.actions.size > 2)
							bindAction(n.bigContentView, R.id.ic_2, n.actions[2])
						if (n.actions.size > 3)
							bindAction(n.bigContentView, R.id.ic_3, n.actions[3])
						if (n.actions.size > 4)
							bindAction(n.bigContentView, R.id.ic_4, n.actions[4])
					}
					else
					{
						Log.d(TAG, "no action")
					}
				} catch (ex:PackageManager.NameNotFoundException) {
					Log.d(TAG, "package " + packageName + "not found")
				}
				// Log.d(TAG, "notification " + n);
				return Decorating.Processed
			}
		}
	}
	companion object {
		private val TAG = "MediaDecorator"
		private fun getLargeIcon(notification:Notification): Bitmap? {
			val icon = notification.getLargeIcon()
			return icon?.let { XposedHelpers.callMethod(notification.getLargeIcon(), "getBitmap") as? Bitmap }
		}
		private fun isMediaNotification(notification:Notification): Boolean {
			if (notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
				return true
			} else {
				return NevoDecoratorService.TEMPLATE_MEDIA.equals(notification.extras.getString(Notification.EXTRA_TEMPLATE))
			}
		}
	}
}
