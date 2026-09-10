import SwiftUI
import FirebaseCore
import FirebaseMessaging
import GoogleMaps
import shared
import UIKit
import UserNotifications

private enum AppRoute {
    case login
    case register
    case home
}

private func syncFcmTokenToSupabase(_ token: String) {
    let uid = IosPlatformSupportKt.iosCurrentSessionUid()
    let accessToken = IosPlatformSupportKt.iosCurrentSessionAccessToken()
    guard !uid.isEmpty, !accessToken.isEmpty,
          let url = URL(string: "https://yytytngfxdssdhozrfwo.supabase.co/rest/v1/device_tokens?on_conflict=token") else { return }
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("sb_publishable_kdt_sI7dzwoKYEzu75gDiQ_Ah9GJw8y", forHTTPHeaderField: "apikey")
    request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
    request.setValue("application/json", forHTTPHeaderField: "Content-Type")
    request.setValue("resolution=merge-duplicates,return=minimal", forHTTPHeaderField: "Prefer")
    request.httpBody = try? JSONSerialization.data(withJSONObject: ["token": token, "user_id": uid, "platform": "ios"])
    URLSession.shared.dataTask(with: request).resume()
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey : Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        Messaging.messaging().delegate = self
        requestNotificationPermission(application)
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNs registration failed: \(error.localizedDescription)")
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        syncFcmToken(fcmToken)
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .badge, .sound])
    }

    private func requestNotificationPermission(_ application: UIApplication) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
    }

    private func syncFcmToken(_ fcmToken: String?) {
        guard let token = fcmToken, !token.isEmpty else { return }
        syncFcmTokenToSupabase(token)
    }
}

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var route: AppRoute = IosPlatformSupportKt.iosHasActiveSession() ? .home : .login
    @State private var currentEmail: String = {
        let email = IosPlatformSupportKt.iosCurrentSessionEmail()
        return email
    }()

    init() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        GMSServices.provideAPIKey("AIzaSyBRVnIMWpOxVIXpzK54xestTg175gm23JY")
    }

    var body: some Scene {
        WindowGroup {
            Group {
                switch route {
                case .home:
                    HomeView(
                        initialEmail: currentEmail,
                        onLogout: {
                            route = .login
                        }
                    )
                case .register:
                    RegisterView(
                        onRegistered: { email in
                            currentEmail = email
                            route = .home
                        },
                        onGoLogin: {
                            route = .login
                        }
                    )
                case .login:
                    LoginView(
                        onLogin: { email, _ in
                            currentEmail = email
                            route = .home
                        },
                        onRegister: {
                            route = .register
                        }
                    )
                }
            }
            .onAppear {
                syncPersistedState()
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    syncPersistedState()
                }
            }
        }
    }

    private func syncPersistedState() {
        currentEmail = IosPlatformSupportKt.iosCurrentSessionEmail()
        route = IosPlatformSupportKt.iosHasActiveSession() ? .home : .login
        syncNotificationToken()
    }

    private func syncNotificationToken() {
        guard !IosPlatformSupportKt.iosCurrentSessionUid().isEmpty else { return }
        Messaging.messaging().token { token, _ in
            guard let token, !token.isEmpty else { return }
            syncFcmTokenToSupabase(token)
        }
    }

}

struct LoginView: UIViewControllerRepresentable {
    var onLogin: (String, String) -> Void
    var onRegister: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.LoginViewController(
            onLogin: { email, password in
                onLogin(email, password)
            },
            onRegister: {
                onRegister()
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct RegisterView: UIViewControllerRepresentable {
    var onRegistered: (String) -> Void
    var onGoLogin: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        RegisterViewControllerKt.RegisterViewController(
            onRegistered: { email in
                onRegistered(email)
            },
            onGoLogin: {
                onGoLogin()
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct HomeView: UIViewControllerRepresentable {
    var initialEmail: String
    var onLogout: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        HomeViewControllerKt.HomeViewController(
            initialEmail: initialEmail,
            onOpenSettings: {},
            onLogout: {
                onLogout()
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
