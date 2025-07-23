package com.example.finbot
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.MenuItem
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.finbot.databinding.ActivityMainBinding
import com.example.finbot.fragment.profileFragment
import com.example.finbot.fragment.statFragment
import com.example.finbot.fragments.homeFragment
import com.example.finbot.fragment.AddExpenseFragment
import com.example.finbot.util.NotificationHelper
import com.example.finbot.util.SharedPreferencesManager
import com.example.finbot.fragment.earningFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefsManager: SharedPreferencesManager
    private lateinit var notificationHelper: NotificationHelper
    private var currentFragment: Fragment? = null

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, initialize notifications
            setupNotifications()
        } else {
            Toast.makeText(this,
                "Notification permission denied. Budget alerts will be disabled.",
                Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        sharedPrefsManager = SharedPreferencesManager.getInstance(this)
        notificationHelper = NotificationHelper.getInstance(this)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        } else {
            setupNotifications()
        }

        // Handle intent extras
        if (intent.getBooleanExtra("OPEN_ADD_EXPENSE", false)) {
            loadFragmentWithAnimation(AddExpenseFragment(), "AddExpense")
            binding.bottomNavigation.selectedItemId = R.id.nav_add
        } else {
            // Load the default fragment (HomeFragment) without animation on first load
            loadFragment(homeFragment())
            currentFragment = homeFragment()
        }

        // Set up bottom navigation with animations
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    val fragment = homeFragment()
                    if (currentFragment !is homeFragment) {
                        loadFragmentWithAnimation(fragment, "Home")
                        currentFragment = fragment
                    }
                    true
                }
                R.id.nav_stat -> {
                    val fragment = statFragment()
                    if (currentFragment !is statFragment) {
                        loadFragmentWithAnimation(fragment, "Stats")
                        currentFragment = fragment
                    }
                    true
                }
                R.id.nav_add -> {
                    val fragment = AddExpenseFragment()
                    if (currentFragment !is AddExpenseFragment) {
                        loadFragmentWithAnimation(fragment, "AddExpense")
                        currentFragment = fragment
                    }
                    true
                }
                R.id.nav_earning -> {
                    val fragment = earningFragment()
                    if (currentFragment !is earningFragment) {
                        loadFragmentWithAnimation(fragment, "Earnings")
                        currentFragment = fragment
                    }
                    true
                }
                R.id.nav_profile -> {
                    val fragment = profileFragment()
                    if (currentFragment !is profileFragment) {
                        loadFragmentWithAnimation(fragment, "Profile")
                        currentFragment = fragment
                    }
                    true
                }
                else -> false
            }
        }

        // Check for budget alerts
        notificationHelper.checkAndShowBudgetAlertIfNeeded()
    }

    private fun requestNotificationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                setupNotifications()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                // Explain why we need notification permission
                Toast.makeText(
                    this,
                    "FinBot needs notification permission to send budget alerts",
                    Toast.LENGTH_LONG
                ).show()
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                // Request the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupNotifications() {
        // Schedule daily reminder if enabled
        if (sharedPrefsManager.isReminderEnabled()) {
            notificationHelper.scheduleDailyReminder()
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for budget alerts every time the app comes to foreground
        notificationHelper.checkAndShowBudgetAlertIfNeeded()
    }

    // Original helper function without animations (for initial load)
    fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    // Enhanced function with smooth animations
    private fun loadFragmentWithAnimation(fragment: Fragment, tag: String) {
        // Add smooth fade and slide animations
        val transaction = supportFragmentManager.beginTransaction()

        // Custom animations for different fragments
        when (fragment) {
            is homeFragment -> {
                // Home gets a special entrance animation
                transaction.setCustomAnimations(
                    R.anim.slide_in_right,     // Enter animation
                    R.anim.slide_out_left,     // Exit animation
                    R.anim.slide_in_left,      // Pop enter animation
                    R.anim.slide_out_right     // Pop exit animation
                )
            }
            is AddExpenseFragment -> {
                // Add expense slides up from bottom
                transaction.setCustomAnimations(
                    R.anim.slide_up_in,
                    R.anim.slide_down_out,
                    R.anim.slide_up_in,
                    R.anim.slide_down_out
                )
            }
            else -> {
                // Default smooth transition for other fragments
                transaction.setCustomAnimations(
                    R.anim.fade_in,
                    R.anim.fade_out,
                    R.anim.fade_in,
                    R.anim.fade_out
                )
            }
        }

        transaction.replace(R.id.fragmentContainer, fragment, tag)
        transaction.commit()
    }

    // Additional method for special home animation (can be called from other activities)
    fun loadHomeWithSpecialAnimation() {
        val homeFragment = homeFragment()
        val transaction = supportFragmentManager.beginTransaction()

        // Special animation for returning to home
        transaction.setCustomAnimations(
            R.anim.zoom_in,
            R.anim.zoom_out,
            R.anim.zoom_in,
            R.anim.zoom_out
        )

        transaction.replace(R.id.fragmentContainer, homeFragment, "Home")
        transaction.commit()

        // Update bottom navigation
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        currentFragment = homeFragment
    }
}