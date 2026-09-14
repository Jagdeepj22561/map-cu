# CU Connect — Project Feature Overview

## 1. Product summary

CU Connect is a cross-platform campus companion for Chandigarh University. It combines an interactive campus map and walking navigation with student accounts, announcements, lost-and-found posts, events, team formation, direct messaging, campus communities, profiles, and safety contacts.

The repository contains:

- An Android application built with Kotlin and Jetpack Compose.
- A Kotlin Multiplatform shared module containing common models, repositories, navigation logic, and Compose UI.
- An iOS application shell that uses the shared Kotlin framework and Google Maps.
- A Supabase backend schema with authentication-aware Row Level Security policies.
- A small Node.js/Express service for college-email OTP registration and authenticated image uploads.

## 2. Campus map and navigation

- Interactive Google Map constrained to the university campus boundary.
- Current-location display, location permission handling, and a recenter control.
- Custom campus map styling that hides irrelevant standard map labels and POIs.
- 28 predefined campus places, including gates, academic blocks, parks, a bank, an ATM, and a hostel.
- Place categories for blocks, gates, parks, hostels, banks, ATMs, cafeterias, parking, libraries, labs, and other locations.
- Search places by name and filter them by category.
- Zoom-aware marker filtering to avoid overcrowding the map.
- Custom labeled markers and a place details panel.
- Route picker with optional destination preselection.
- 179 listed campus routes, exposed in both directions for a total of 358 route variants.
- Active-route polyline, current instruction, remaining distance, start/destination markers, and stop-navigation control.
- Navigation camera following and nearest-route-segment calculations.
- Arrival detection when the user is within approximately 15 metres of the destination.
- Optional text-to-speech voice instructions with duplicate-instruction suppression.

## 3. Authentication and account access

- Email/password login through Supabase Auth.
- Registration restricted by the backend to `@cuchd.in` college email addresses.
- Six-digit email OTP registration flow.
- OTP expiry, resend cooldown, and daily request limit.
- Profile creation after registration.
- Profile-photo selection, compression, and authenticated upload.
- Forgot-password email flow.
- Password recovery through the `maps123://auth` deep link and an in-app new-password dialog.
- Persisted login session and explicit logout.
- Guest mode for map access without an account.
- Login gates around private features such as chat, announcements, profiles, and settings.

## 4. Announcements and campus feed

- Three content sections: For You, Lost & Found, and Events.
- Create text/image posts.
- Dedicated lost-and-found fields for item, place, time, and reward.
- Dedicated event fields for venue, time, purpose, duty-leave type, solo/team mode, team size, departments, external link, and category.
- Search and filter the feed.
- Post detail view with view counts.
- Like/unlike posts.
- Add, reply to, copy, delete, and report comments.
- Save/bookmark posts locally and view saved posts from the profile.
- Share through other installed apps, copy a deep link, or send a post directly to a friend in chat.
- Open shared posts through `maps123://post/{postId}` or the configured HTTPS deep link.
- Contact a post author privately when the users are friends.
- Report or delete a post where permissions allow it.
- Local Room cache plus Supabase refresh/realtime synchronization for faster loading and limited offline visibility.

## 5. Events and team collaboration

- Events hub with search and filters for mode, status, and category.
- Event categories include hackathon, workshop, cultural, sports, tech talk, seminar, quiz, and other.
- Upcoming, live, and ended event states.
- Event detail sections for overview, timeline, teams, and rules/information.
- Register for and withdraw from events.
- Participant and team counts.
- Organizers can add or remove rounds/milestones with date, time, description, and ordering.
- Create and rename an event team with an optional member limit.
- Browse open teams and see available capacity.
- Request to join a team with an introduction message, withdraw a request, and track request status.
- Team owners can approve/reject join requests, invite friends, remove members, or disband the team.
- Members can leave a team.
- Post teammate/skill requirements and respond with “I’m interested.”
- View people who responded to a requirement and initiate friend requests.
- Duty-leave, eligibility, coordinator, and contact information sections.

## 6. Friends and people discovery

- Send friend requests by email or from user discovery surfaces.
- Incoming and outgoing friend-request handling.
- Accept, reject, and remove friends.
- Nearby-user discovery based on the current location.
- Nearby refresh throttling and distance display.
- Suggested connections based on shared academic/campus profile signals.
- Global user search.
- Ghost mode to hide the current user from nearby discovery.
- Open another student's profile and start a private conversation when allowed.

## 7. Direct messaging

- One-to-one chats between friends.
- Conversation list with last-message time and unread badges.
- Search conversations and messages.
- Text and image messages.
- Image preview before sending and full-screen image viewing.
- Local Room message cache and background Supabase synchronization.
- Pagination for older messages and explicit read-state updates.
- Copy messages.
- Edit recently sent messages (currently limited to a two-minute window in the Android UI).
- Delete selected messages locally or for everyone.
- Clear a conversation or remove it from the local chat list.
- Block and unblock users.
- Report chat/user activity from the conversation UI.
- Recognize shared post links inside messages and open the related post.
- Firebase Cloud Messaging notifications for new messages.
- Notification taps deep-link directly into the relevant chat.

## 8. Communities and group chat

- Community directory embedded under the Chat → Community tab.
- Search communities and filter between all, joined, and explore views.
- Create a community with a name and description.
- Join directly or by invite code/shareable link.
- Copy invite codes and links.
- Community group chat with message search and refresh.
- Browse community information and member lists.
- Add eligible campus users.
- Message a member privately.
- Owner/admin/member roles.
- Promote or demote administrators and remove members where authorized.
- Delete individual group messages.
- Leave a community or permanently delete an owned community.

## 9. Profiles

- View personal and other-user profiles.
- Editable name, phone, course, year, semester, university, date of birth, gender, and profile photo.
- Instagram, Snapchat, and LinkedIn links.
- Profile sections for details and authored posts.
- Personal Saved section for bookmarked posts.
- Like, comment on, share, report, save, or delete posts from profile views.
- Find-friends and start-chat actions.
- Blocked-user state handling.
- Student/profile badges in shared UI components.

## 10. Search and discovery

- Map-specific place search.
- Chat search across friend names and recent message text.
- Feed search across announcements.
- Global search across users, posts, and communities.
- Search results can open a profile, post, community, or map destination.

## 11. Settings, accessibility, and safety

- Dark-mode preference.
- Voice-navigation preference.
- Ghost-mode privacy preference.
- My Profile shortcut.
- Direct-dial campus helplines for general support, girls' hostels, boys' hostels, transport, and shuttle services.
- Email-based issue reporting.
- Logout and session cleanup.
- iOS-only debug-log entry point in the shared settings UI when supplied by the host application.

## 12. Data, offline behavior, and security

- Supabase Auth, PostgREST, Realtime, and Storage clients.
- PostgreSQL tables for profiles, announcements, likes, comments, reports, friendships, friend requests, direct chats, messages, groups, blocks, event teams, team requirements, and device tokens.
- Row Level Security policies and database functions for protected social operations.
- Room database on Android for routes, places, users/profiles, announcements, chats, messages, and friend requests.
- Preloaded place and route data.
- Cached feeds, profiles, chats, and messages to reduce startup/network latency.
- FCM device-token synchronization.
- Authenticated image-upload endpoint with MIME/type checks and a 5 MB limit.
- Backend-wide request rate limiting.
- The Supabase service-role key remains server-side; mobile clients receive only public client configuration.

## 13. Platform coverage

### Android

Android is the most complete host. It wires together maps, navigation, authentication, guest mode, announcements, event/team screens, direct chat, communities, profiles, settings, local Room caching, FCM notifications, deep links, image uploads, and native phone/email intents.

### iOS

The iOS target uses the Kotlin Multiplatform shared module and includes shared Compose screens, Supabase-backed authentication/social repositories, a Google Maps host, map/location support, navigation, chat, communities, announcements, profiles, settings, and iOS platform integrations. Some Android-specific behavior—most notably Room storage and Firebase message notification handling—is implemented differently or is not present in the iOS host.

## 14. External services and required configuration

- Google Maps SDK and a valid Maps API key.
- Supabase URL and publishable key in local properties or environment variables.
- Applied Supabase SQL migrations.
- Render or another Node.js host for the Express backend.
- Brevo API credentials and verified sender for OTP email delivery.
- Supabase service-role key on the backend only.
- Cloudinary credentials for image uploads.
- Firebase project configuration for Android push notifications.

## 15. Important implementation notes

- The root `README.md` currently contains only the old project name, so this document is the authoritative feature summary.
- The OTP store is an in-memory development implementation. OTPs are lost on backend restart and are not shared across multiple server instances; a managed Redis/KV store is recommended before scaling.
- Saved/bookmarked announcements are stored locally on Android rather than synchronized to the user's Supabase account.
- The HTTPS post deep-link host uses `maps123.example.com`, which is a placeholder unless that domain is configured and verified.
- Several route names display mojibake (`â†’`) in source text and should be normalized to the proper arrow character or plain `->`.
- The Google Maps API key is currently embedded directly in `AndroidManifest.xml`; it should be restricted in Google Cloud and preferably injected through build configuration.
- Automated tests are currently only template/sample tests, so feature behavior is mostly verified through implementation and manual testing rather than a broad automated suite.

