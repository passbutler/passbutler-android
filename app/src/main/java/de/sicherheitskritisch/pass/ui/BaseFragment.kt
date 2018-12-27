package de.sicherheitskritisch.pass.ui

import android.content.Context
import android.support.v4.app.Fragment
import de.sicherheitskritisch.pass.MainActivity

open class BaseFragment : Fragment(), FragmentPresenting, MainActivity.OnBackPressedListener {
    var fragmentPresentingHelper: FragmentPresentingHelper? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        (activity as? MainActivity)?.addOnBackPressedListener(this)
    }

    override fun onDetach() {
        (activity as? MainActivity)?.removeOnBackPressedListener(this)
        super.onDetach()
    }

    override fun showFragment(fragment: Fragment, replaceFragment: Boolean, addToBackstack: Boolean) {
        fragmentPresentingHelper?.showFragment(fragment, replaceFragment, addToBackstack)
    }

    override fun popBackstack() {
        fragmentPresentingHelper?.popBackstack()
    }

    override fun onHandleBackPress(): Boolean {
        return false
    }
}