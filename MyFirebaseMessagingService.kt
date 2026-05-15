import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // 1. لاستقبال الإشعارات
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        }
    }

    // 2. لإدارة الرموز المميزة (Token)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // أرسل هذا token إلى خادمك (Server) هنا
    }

    private fun sendNotification(title: String?, body: String?) {
        // منطق عرض الإشعار
    }
}