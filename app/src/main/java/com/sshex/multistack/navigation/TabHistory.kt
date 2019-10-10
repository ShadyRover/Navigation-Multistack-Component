package com.sshex.multistack.navigation

import java.io.Serializable

class TabHistory : Serializable {

	private val stack: ArrayList<Int> = ArrayList()

	private val isEmpty: Boolean
		get() = stack.size == 0

	val size: Int
		get() = stack.size

	fun push(entry: Int): Boolean {
		if (!stack.contains(entry)) {
			stack.add(entry)
			return true
		} else {
			stack.remove(entry)
			stack.add(entry)
		}
		return false
	}

	fun popPrevious(): Int {
		var entry = -1
		if (!isEmpty) {
			entry = stack[stack.size - 2]
			stack.removeAt(stack.size - 2)
		}
		return entry
	}

	fun getLast(): Int {
		return stack.last()
	}

	fun clear() {
		stack.clear()
	}
}