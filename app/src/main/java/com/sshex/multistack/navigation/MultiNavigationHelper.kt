package com.sshex.multistack.navigation

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.annotation.NavigationRes
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.sshex.multistack.R
import com.sshex.multistack.navigation.extensions.resetStackHard

/**
 * Helps showing multiple navigation graphs in a single view.
 *
 *
 * Hopefully this class will no longer be necessary in a future version of the navigation component.
 */
class MultiNavigationHelper
/**
 * Creates a new navigation helper.
 *
 * @param containerId     The container Id.
 * @param fragmentManager The fragment manager.
 */
private constructor(
    @param:IdRes private val containerId: Int,
    private val fragmentManager: FragmentManager
) {
    lateinit var finishListener: FinishListener
    private var commonNavigatorId: Int = 0
    private var currentTag: String? = null
    private val selectedNavController = MutableLiveData<NavController>()
    val barActionLiveData = MutableLiveData<BarAction>()
    private var rootFragmentTag: String? = null
    @SuppressLint("UseSparseArrays")
    private val fragmentTagMap = HashMap<Int, String>()
    private val tabHistory = TabHistory()

    /**
     * Gets the current nav controller live data.
     *
     * @return The current nav controller live data.
     */
    val currentNavController: LiveData<NavController>
        get() = selectedNavController

    /**
     * Inits the helper
     *
     * @param selectedGraphId     The current graph Id.
     * @param navigationResources The navigation resources.
     */
    private fun init(
        @IdRes selectedGraphId: Int, @NavigationRes commonNavigator: Int,
        @NavigationRes
        vararg navigationResources: Int
    ) {
        for (i in navigationResources.indices) {
            val fragmentTag = getFragmentTag(navigationResources[i])
            if (i == 0) {
                rootFragmentTag = fragmentTag
            }

            val navHostFragment = obtainNavHostFragment(fragmentTag, navigationResources[i])
            val graphId = navHostFragment.navController.graph.id
            fragmentTagMap[graphId] = fragmentTag

            if (selectedGraphId == graphId) {
                val transaction = fragmentManager.beginTransaction().attach(navHostFragment)
                if (i == 0) {
                    transaction.setPrimaryNavigationFragment(navHostFragment)
                }
                transaction.commitNow()
                selectedNavController.setValue(navHostFragment.navController)
            } else {
                fragmentManager.beginTransaction().detach(navHostFragment).commitNow()
            }
        }

        //init common stack
        val fragmentTag = getFragmentTag(commonNavigator)
        val navHostFragment = obtainNavHostFragment(fragmentTag, commonNavigator)
        val graphId = navHostFragment.navController.graph.id
        this.commonNavigatorId = graphId
        fragmentTagMap[graphId] = fragmentTag

        if (selectedGraphId == graphId) {
            val transaction = fragmentManager.beginTransaction().attach(navHostFragment)
            transaction.setPrimaryNavigationFragment(navHostFragment)
            transaction.commitNow()
            selectedNavController.setValue(navHostFragment.navController)
        } else {
            fragmentManager.beginTransaction().detach(navHostFragment).commitNow()
        }

        fragmentManager.addOnBackStackChangedListener {
            // Reset the graph if the currentDestination is not valid (happens when the back
            // stack is popped after using the back button).
            val navController = selectedNavController.value
            if (navController != null && navController.currentDestination == null) {
                navController.navigate(navController.graph.id)
            }
        }
    }

    /**
     * Resets the current selected graph to the start destination.
     */
    fun resetCurrentGraph() {
        val navController = currentNavController.value
        navController?.popBackStack(navController.graph.startDestination, false)
    }

    /**
     * Navigates up.
     *
     * @return `true` if the graph was able to navigateToStack up, otherwise `false`.
     */
    fun navigateUp(): Boolean {
        return if (currentNavController.value == null) {
            false
        } else currentNavController.value!!
            .navigateUp()

    }

    /**
     * Attempts to pop the controller's back stack.
     *
     * @return `true` if the stack was popped, otherwise `false`.
     */
    fun popBackStack(): Boolean {
        return if (currentNavController.value == null) {
            false
        } else currentNavController.value!!.popBackStack()

    }

    /**
     * Called to handle deep linking from an intent.
     *
     * @param intent The intent.
     */
    fun deepLink(intent: Intent?) {
        if (intent == null) {
            return
        }
        fragmentTagMap.values.forEach {
            val navHostFragment = findNavHostFragment(it)
            if (navHostFragment != null && navHostFragment.navController.handleDeepLink(intent)) {
                navigateToStack(navHostFragment.navController.graph.id)
            }
        }
    }

    /**
     * Navigates to the navigation graph.
     *
     * @param graphId The navigation's graph Id.
     */
    @JvmOverloads
    fun navigateToStack(@IdRes graphId: Int, addToHistory: Boolean = true) {
        if (fragmentManager.isStateSaved) {
            return
        }

        if (addToHistory) {
            tabHistory.push(graphId)
        }
        val fragmentTag = fragmentTagMap[graphId]
        val selectedFragment = findNavHostFragment(fragmentTag!!)

        selectedNavController.postValue(selectedFragment!!.navController)
        barActionLiveData.postValue(BarAction(graphId, addToHistory))

        // If not the root, attach the fragment and clean up the back stack
        if (currentTag != fragmentTag) {
            val transaction = fragmentManager.beginTransaction()
                .attach(selectedFragment)
                .setPrimaryNavigationFragment(selectedFragment)
                .setCustomAnimations(
                    R.anim.nav_default_enter_anim,
                    R.anim.nav_default_exit_anim,
                    R.anim.nav_default_pop_enter_anim,
                    R.anim.nav_default_pop_exit_anim
                )
                .setReorderingAllowed(true)
            currentTag?.let {
                val findNavHostFragment = findNavHostFragment(it)!!
                if (findNavHostFragment.navController.graph.id == commonNavigatorId) {
                    findNavHostFragment.navController.resetStackHard()
                }
                transaction.detach(findNavHostFragment)
            }
            transaction.commitNow()
            currentTag = selectedFragment.tag
        }
    }

    private fun findFragmentTagContainsDestination(destination: Int): String {
        val listGraphs: List<String> = fragmentTagMap.values.filter {
            val selectedFragment = findNavHostFragment(it)
            selectedFragment?.findNavController()?.graph?.findNode(destination) != null
        }
        val size = listGraphs.size
        require(size != 0) {
            ("navigation destination $destination"
                    + " is unknown to this NavController")
        }
        require(size <= 1) {
            ("navigation destination $destination"
                    + " must belong only for one NavController")
        }
        return listGraphs.first()
    }

    fun newRootScreen(destination: Int) {
        if (fragmentManager.isStateSaved) {
            return
        }

        val fragmentTag = findFragmentTagContainsDestination(destination)
        val selectedFragment = findNavHostFragment(fragmentTag)

        selectedNavController.postValue(selectedFragment!!.navController)

        val navController = selectedFragment.findNavController()
        navController.resetStackHard()
        currentTag = selectedFragment.tag
        if (navController.graph.findNode(destination) != null) {
            navController.navigate(destination)
        } else {
            attachSelectedController(selectedFragment)
        }

    }

    private fun attachSelectedController(selectedFragment: NavHostFragment) {
        val primaryNavigationFragment = fragmentManager.primaryNavigationFragment
        val tag = primaryNavigationFragment?.tag
        val currentNavHostFragment = tag?.run { findNavHostFragment(tag) }
        currentTag = selectedFragment.tag

        val navController = selectedFragment.navController
        selectedNavController.postValue(navController)
        barActionLiveData.postValue(BarAction(navController.graph.id, false))
        val beginTransaction = fragmentManager.beginTransaction()
        beginTransaction.apply {
            currentNavHostFragment?.let {
                detach(currentNavHostFragment)
            }
            attach(selectedFragment)
            setPrimaryNavigationFragment(selectedFragment)
            addToBackStack(tag)
            setCustomAnimations(
                R.anim.nav_default_enter_anim,
                R.anim.nav_default_exit_anim,
                R.anim.nav_default_pop_enter_anim,
                R.anim.nav_default_pop_exit_anim
            )
            setReorderingAllowed(true)
            commit()
        }
    }

    fun navigateTo(destination: Int, bundle: Bundle? = null) {
        if (fragmentManager.isStateSaved) {
            return
        }
        val primaryNavigationFragment = fragmentManager.primaryNavigationFragment
        val tag = primaryNavigationFragment?.tag!!

        val currentNavHostFragment = findNavHostFragment(tag)

        val findNavController = primaryNavigationFragment.findNavController()
        if (findNavController.graph.findNode(destination) != null) {
            findNavController.navigate(destination, bundle)
        } else {
            fragmentTagMap.values.find {
                val selectedFragment = findNavHostFragment(it)
                selectedFragment?.findNavController()?.graph?.findNode(destination) != null
            }?.let { found ->
                val selectedFragment = findNavHostFragment(found)!!
                currentTag = selectedFragment.tag
                val navController = selectedFragment.navController
                selectedNavController.postValue(navController)
                barActionLiveData.postValue(BarAction(navController.graph.id, false))
                fragmentManager.beginTransaction()
                    .attach(selectedFragment)
                    .setPrimaryNavigationFragment(selectedFragment)
                    .detach(currentNavHostFragment!!)
                    .setCustomAnimations(
                        R.anim.nav_default_enter_anim,
                        R.anim.nav_default_exit_anim,
                        R.anim.nav_default_pop_enter_anim,
                        R.anim.nav_default_pop_exit_anim
                    )
                    .setReorderingAllowed(true)
                    .commitNow()
                if (commonNavigatorId == navController.graph.id) {
                    navController.resetStackHard()
                }
                navController.navigate(destination, bundle)
            }
        }
    }

    fun navigateTo(destination: NavDirections) {
        val primaryNavigationFragment = fragmentManager.primaryNavigationFragment
        val tag = primaryNavigationFragment?.tag!!

        val currentNavHostFragment = findNavHostFragment(tag)

        val findNavController = primaryNavigationFragment.findNavController()
        val action = findNavController.currentDestination?.getAction(destination.actionId)
        if (action != null) {
            findNavController.navigate(destination)
        } else {
            fragmentTagMap.values.find {
                val selectedFragment = findNavHostFragment(it)
                selectedFragment?.findNavController()?.currentDestination?.getAction(destination.actionId) != null
            }?.let { found ->
                val selectedFragment = findNavHostFragment(found)!!
                currentTag = selectedFragment.tag
                val navController = selectedFragment.navController
                navController.navigate(destination)
                tabHistory.push(navController.graph.id)
                selectedNavController.postValue(navController)
                barActionLiveData.postValue(BarAction(navController.graph.id, false))
                fragmentManager.beginTransaction()
                    .attach(selectedFragment)
                    .setPrimaryNavigationFragment(selectedFragment)
                    .detach(currentNavHostFragment!!)
                    .addToBackStack(tag)
                    .setCustomAnimations(
                        R.anim.nav_default_enter_anim,
                        R.anim.nav_default_exit_anim,
                        R.anim.nav_default_pop_enter_anim,
                        R.anim.nav_default_pop_exit_anim
                    )
                    .setReorderingAllowed(true)
                    .commit()
                fragmentManager.executePendingTransactions()
                if (commonNavigatorId == navController.graph.id) {
                    navController.resetStackHard()
                }
                navController.navigate(destination)
            }
        }
    }

    /**
     * Helper method to either get or create a nav host fragment.
     *
     * @param fragmentTag The fragment's tag.
     * @param navGraphId  The graph resource.
     * @return The nav host fragment.
     */
    private fun obtainNavHostFragment(
        fragmentTag: String,
        @NavigationRes navGraphId: Int
    ): NavHostFragment {
        val existingFragment = fragmentManager.findFragmentByTag(fragmentTag)
        if (existingFragment != null) {
            return existingFragment as NavHostFragment
        }

        val navHostFragment = NavHostFragment.create(navGraphId)
        fragmentManager.beginTransaction()
            .add(containerId, navHostFragment, fragmentTag)
            .commitNow()

        return navHostFragment
    }

    /**
     * Helper method to find the navigation host fragment.
     *
     * @param fragmentTag The fragment's tag.
     * @return The nav host fragment.
     */
    private fun findNavHostFragment(fragmentTag: String): NavHostFragment? {
        return fragmentManager.findFragmentByTag(fragmentTag) as NavHostFragment?
    }

    /**
     * Gets the fragment tag based on the navigation Id.
     *
     * @param navigationId The navigation ID.
     * @return The fragment's tag.
     */
    private fun getFragmentTag(@NavigationRes navigationId: Int): String {
        return "MultiNavigationHelper#$navigationId"
    }

    fun onBackPressed() {
        val currentController = currentNavController.value
        currentController?.run {
            if (currentController.graph.id == commonNavigatorId) {
                popCommonStack(currentController)
            } else {
                if (currentDestination != null && graph.startDestination != currentDestination?.id) {
                    popBackStack()
                } else {
                    if (tabHistory.size > 1) {
                        switchNavHost(tabHistory.popPrevious(), false)
                    } else {
                        finishListener.onFinish()
                    }
                }
            }

        }

    }

    private fun popCommonStack(currentController: NavController) {
        with(currentController) {
            if (checkStackIsEmpty() && !restoreTabStack(currentController)) {
                finishListener.onFinish()
            } else if (checkStackIsNotEmpty()) {
                popBackStack()
                if (checkStackIsEmpty() && !restoreTabStack(currentController)) {
                    finishListener.onFinish()
                }
            }
        }
    }

    private fun NavController.checkStackIsNotEmpty() =
        currentDestination != null && currentDestination?.navigatorName != "navigation"

    private fun NavController.checkStackIsEmpty() = checkStackIsNotEmpty().not()

    private fun restoreTabStack(currentController: NavController): Boolean {
        currentController.resetStackHard()
        if (tabHistory.size > 0) {
            switchNavHost(tabHistory.getLast(), false)
            return true
        }
        return false
    }

    private fun switchNavHost(graphId: Int, addToHistory: Boolean) {
        navigateToStack(graphId, addToHistory)
    }

    interface FinishListener {
        fun onFinish()
    }

    companion object {

        /**
         * Factory method.
         *
         * @param containerId         The container Id to load the nav host fragments into.
         * @param fragmentManager     The fragment manager.
         * @param selectedGraphId     The current selected graph Id.
         * @param navigationResources The navigation graph resources.
         * @return The navigation helper.
         */
        @JvmStatic
        fun newHelper(
            @IdRes containerId: Int,
            fragmentManager: FragmentManager,
            @IdRes selectedGraphId: Int,
            @NavigationRes commonNavigator: Int,
            @NavigationRes vararg navigationResources: Int
        ): MultiNavigationHelper {

            val helper = MultiNavigationHelper(containerId, fragmentManager)
            helper.init(selectedGraphId, commonNavigator, *navigationResources)
            return helper
        }
    }
}