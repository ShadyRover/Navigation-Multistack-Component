package com.sshex.multistack.navigation

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.bottomnavigation.BottomNavigationView

class BottomNavView @JvmOverloads constructor(context: Context,
                                              attrs: AttributeSet? = null,
                                              defStyleAttr: Int = 0
) : BottomNavigationView(context, attrs, defStyleAttr) {
	fun setSelectedItemId(itemId: Int, fromUser: Boolean) {
		if (!fromUser) {
			val item = this.menu.findItem(itemId)
			item?.isChecked = true
		} else {
			super.setSelectedItemId(itemId)
		}
	}
}