import SwiftUI
import FirebaseCore
import FirebaseMessaging
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
        NSSetUncaughtExceptionHandler { exception in
            let symbols = exception.callStackSymbols.joined(separator: "\n")
            let text = "=== FATAL CRASH REPORT ===\nException: \(exception.name.rawValue)\nReason: \(exception.reason ?? "Unknown")\n\nCall Stack:\n\(symbols)"
            UserDefaults.standard.set(text, forKey: "last_crash_log")
            UserDefaults.standard.synchronize()
        }

        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        UNUserNotificationCenter.current().delegate = self
        if FirebaseApp.app() != nil {
            Messaging.messaging().delegate = self
        }
        requestNotificationPermission(application)
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        if FirebaseApp.app() != nil {
            Messaging.messaging().apnsToken = deviceToken
        }
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
    @State private var previousCrashLog: String? = nil
    @State private var showCrashReportSheet: Bool = false
    @State private var crashLogCopied: Bool = false

    init() {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
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
                            print("iOSApp: Transitioning to Home after login for \(email)")
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
                checkPreviousCrashLog()
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    syncPersistedState()
                    checkPreviousCrashLog()
                }
            }
            .sheet(isPresented: $showCrashReportSheet) {
                if let log = previousCrashLog {
                    NavigationView {
                        VStack(spacing: 16) {
                            Text("The application recorded an error or crash. You can copy the diagnostic details below:")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .padding(.horizontal)

                            ScrollView {
                                Text(log)
                                    .font(.system(.caption, design: .monospaced))
                                    .padding()
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .background(Color(UIColor.secondarySystemBackground))
                                    .cornerRadius(10)
                                    .padding(.horizontal)
                            }

                            HStack(spacing: 12) {
                                Button(action: {
                                    UIPasteboard.general.string = log
                                    crashLogCopied = true
                                }) {
                                    HStack {
                                        Image(systemName: crashLogCopied ? "checkmark" : "doc.on.doc")
                                        Text(crashLogCopied ? "Copied to Clipboard!" : "Copy Full Log")
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(crashLogCopied ? Color.green : Color.blue)
                                    .foregroundColor(.white)
                                    .cornerRadius(12)
                                    .fontWeight(.semibold)
                                }

                                Button(action: {
                                    UserDefaults.standard.removeObject(forKey: "last_crash_log")
                                    previousCrashLog = nil
                                    showCrashReportSheet = false
                                }) {
                                    Text("Dismiss")
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .background(Color(UIColor.systemGray5))
                                        .foregroundColor(.primary)
                                        .cornerRadius(12)
                                }
                            }
                            .padding(.horizontal)
                            .padding(.bottom, 12)
                        }
                        .navigationTitle("Diagnostic Log")
                        .navigationBarTitleDisplayMode(.inline)
                    }
                }
            }
        }
    }

    private func checkPreviousCrashLog() {
        if let saved = UserDefaults.standard.string(forKey: "last_crash_log"), !saved.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            previousCrashLog = saved
            showCrashReportSheet = true
        }
    }

    private func syncPersistedState() {
        currentEmail = IosPlatformSupportKt.iosCurrentSessionEmail()
        route = IosPlatformSupportKt.iosHasActiveSession() ? .home : .login
        syncNotificationToken()
    }

    private func syncNotificationToken() {
        guard !IosPlatformSupportKt.iosCurrentSessionUid().isEmpty else { return }
        guard FirebaseApp.app() != nil else { return }
        DispatchQueue.main.async {
            Messaging.messaging().token { token, error in
                if let error = error {
                    print("Firebase token error: \(error.localizedDescription)")
                    return
                }
                guard let token = token, !token.isEmpty else { return }
                syncFcmTokenToSupabase(token)
            }
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
            },
            onContinueAsGuest: {
                onLogin("guest@example.com", "guest")
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
