package com.example.shared.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PureProfileScreen(
    isReadOnly: Boolean,
    isLoading: Boolean,
    isBlocked: Boolean,
    isEditing: Boolean,
    onToggleEditing: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onImageClick: () -> Unit,

    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    profilePicUrl: String,

    course: String,
    onCourseChange: (String) -> Unit,
    year: String,
    onYearChange: (String) -> Unit,
    semester: String,
    onSemesterChange: (String) -> Unit,
    university: String = "",
    onUniversityChange: (String) -> Unit = {},
    dob: String,
    onDobChange: (String) -> Unit,
    gender: String = "",
    onGenderChange: (String) -> Unit = {},
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,

    instagramLink: String,
    onInstagramChange: (String) -> Unit,
    snapchatLink: String,
    onSnapchatChange: (String) -> Unit,
    linkedinLink: String,
    onLinkedinChange: (String) -> Unit,
    onSocialClick: (String, String) -> Unit,
    ghostMode: Boolean = false,
    onGhostModeChange: (Boolean) -> Unit = {},
    onFindFriends: () -> Unit = {},
    onShareProfile: () -> Unit = {},

    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<String>,

    renderPosts: @Composable () -> Unit,
    renderImage: @Composable (String, Modifier) -> Unit
) {

    if (isLoading) {
        Box(
            Modifier.fillMaxSize(),
            Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (isBlocked) {
        Box(
            Modifier.fillMaxSize(),
            Alignment.Center
        ) {
            Text("Blocked user")
        }
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShareProfile) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share profile"
                        )
                    }
                }
            )
        }
    ) { pad ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    top = pad.calculateTopPadding(),
                    end = 16.dp,
                    bottom = pad.calculateBottomPadding()
                )
                .verticalScroll(rememberScrollState())
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------
            // Cover + Avatar
            // ---------------------------------------------------------

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(125.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFD51F32),
                                    Color(0xFFFF6871),
                                    Color(0xFFFFB199)
                                )
                            )
                        )
                        .clip(RoundedCornerShape(16.dp))
                )

                Box(
                    Modifier
                        .size(130.dp)
                        .align(Alignment.BottomCenter)
                ) {

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    val imgMod = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            4.dp,
                            MaterialTheme.colorScheme.surface,
                            CircleShape
                        )
                        .clickable(enabled = !isReadOnly) {
                            onImageClick()
                        }

                    renderImage(profilePicUrl, imgMod)

                    if (!isReadOnly) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .clickable {
                                    onImageClick()
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                "Change profile photo",
                                Modifier.padding(7.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------------------------------------------------
            // Name / Email
            // ---------------------------------------------------------

            if (isEditing) {

                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = {
                        Text("Name")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

            } else {

                Text(
                    name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------
            // Edit / Find Friends
            // ---------------------------------------------------------

            if (!isReadOnly) {

                if (!isEditing) {

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Button(
                            onClick = {
                                onToggleEditing(true)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {

                            Icon(
                                Icons.Default.Edit,
                                null,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text("Edit Profile")
                        }

                        OutlinedButton(
                            onClick = onFindFriends,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {

                            Icon(
                                Icons.Default.PersonAdd,
                                null,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text("Find Friends")
                        }
                    }

                } else {

                    Row(
                        Modifier.fillMaxWidth()
                    ) {

                        OutlinedButton(
                            onClick = {
                                onToggleEditing(false)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------
            // Tabs
            // ---------------------------------------------------------

            TabRow(
                selectedTabIndex = selectedTab,
                indicator = { pos ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(pos[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {

                tabs.forEachIndexed { i, t ->

                    Tab(
                        selected = selectedTab == i,
                        onClick = {
                            onTabSelected(i)
                        },
                        text = {
                            Text(
                                t,
                                fontWeight =
                                    if (selectedTab == i) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---------------------------------------------------------
            // Details
            // ---------------------------------------------------------

            if (selectedTab == 0) {

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // =================================================
                    // ACADEMIC
                    // 2 COLUMNS × 2 ROWS
                    // =================================================

                    ProfileSection(
                        "Academic",
                        Icons.Outlined.School
                    ) {

                        // Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileInfoRow(
                                    label = "Course",
                                    value = course,
                                    icon = Icons.Outlined.Book,
                                    isEditing = isEditing,
                                    onValueChange = onCourseChange
                                )
                            }

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileInfoRow(
                                    label = "Year",
                                    value = year,
                                    icon = Icons.Outlined.CalendarToday,
                                    isEditing = isEditing,
                                    onValueChange = onYearChange
                                )
                            }
                        }

                        // Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileInfoRow(
                                    label = "Semester",
                                    value = semester,
                                    icon = Icons.Outlined.Timeline,
                                    isEditing = isEditing,
                                    onValueChange = onSemesterChange
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ProfileInfoRow(
                                    label = "University",
                                    value = university,
                                    icon = Icons.Outlined.AccountBalance,
                                    isEditing = isEditing,
                                    onValueChange = onUniversityChange
                                )
                            }
                        }
                    }

                    // =================================================
                    // PERSONAL
                    // 2 COLUMNS × 2 ROWS
                    // =================================================

                    ProfileSection(
                        "Personal",
                        Icons.Outlined.PersonOutline
                    ) {

                        // Row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileInfoRow(
                                    label = "DOB",
                                    value = dob,
                                    icon = Icons.Outlined.Cake,
                                    isEditing = isEditing,
                                    onValueChange = onDobChange
                                )
                            }

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileInfoRow(
                                    label = "Email",
                                    value = email,
                                    icon = Icons.Outlined.Email,
                                    isEditing = false,
                                    onValueChange = {}
                                )
                            }
                        }

                        // Row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                ProfileInfoRow(
                                    label = "Phone",
                                    value = phoneNumber,
                                    icon = Icons.Outlined.Phone,
                                    isEditing = isEditing,
                                    onValueChange = onPhoneNumberChange
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                ProfileInfoRow(
                                    label = "Gender",
                                    value = gender,
                                    icon = Icons.Outlined.Wc,
                                    isEditing = isEditing,
                                    onValueChange = onGenderChange
                                )
                            }
                        }
                    }

                    // =================================================
                    // SOCIAL
                    // UNCHANGED
                    // =================================================

                    ProfileSection(
                        "Social",
                        Icons.Outlined.Share
                    ) {

                        SocialLinkRow(
                            "Instagram",
                            instagramLink,
                            Icons.Outlined.CameraAlt,
                            isEditing,
                            onInstagramChange
                        ) {
                            onSocialClick(
                                "instagram",
                                instagramLink
                            )
                        }

                        SocialLinkRow(
                            "LinkedIn",
                            linkedinLink,
                            Icons.Outlined.WorkOutline,
                            isEditing,
                            onLinkedinChange
                        ) {
                            onSocialClick(
                                "linkedin",
                                linkedinLink
                            )
                        }
                    }

                    // =================================================
                    // PRIVACY
                    // UNCHANGED
                    // =================================================

                    if (!isReadOnly) {

                        ProfileSection(
                            "Privacy",
                            Icons.Outlined.Lock
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    Icons.Outlined.LocationOff,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.width(12.dp))

                                Column(
                                    Modifier.weight(1f)
                                ) {

                                    Text(
                                        "Ghost Mode",
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Text(
                                        "Hide your location from nearby users",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Switch(
                                    checked = ghostMode,
                                    onCheckedChange = onGhostModeChange
                                )
                            }
                        }
                    }
                }

            } else {

                renderPosts()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}


/* ================================================================
   PROFILE SECTION
   ================================================================ */

@Composable
fun ProfileSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
                    .copy(alpha = 0.3f)
        )
    ) {

        Column(
            Modifier.padding(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(12.dp))

                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            content()
        }
    }
}


/* ================================================================
   PROFILE INFO ROW
   ================================================================ */

@Composable
fun ProfileInfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    isEditing: Boolean,
    onValueChange: (String) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEditing) {

                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

            } else {

                Text(
                    text = value.ifBlank {
                        "Not set"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}


/* ================================================================
   SOCIAL LINK ROW
   ================================================================ */

@Composable
fun SocialLinkRow(
    label: String,
    link: String,
    icon: ImageVector,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary
        )

        Spacer(Modifier.width(12.dp))

        Column {

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isEditing) {

                OutlinedTextField(
                    value = link,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    placeholder = {
                        Text("https://...")
                    }
                )

            } else {

                Text(
                    text = if (link.isBlank()) {
                        "Not connected"
                    } else {
                        "Connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color =
                        if (link.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    modifier = Modifier.clickable(
                        enabled = link.isNotBlank()
                    ) {
                        onClick()
                    }
                )
            }
        }
    }
}
