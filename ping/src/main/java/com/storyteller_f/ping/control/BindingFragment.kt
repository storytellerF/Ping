package com.storyteller_f.ping.control

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BindingFragment<B : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> B
) : Fragment() {
    private var binding: B? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflate(inflater, container, false).also { binding = it }.root
    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        onBindViewEvent(requireNotNull(binding))
    }
    abstract fun onBindViewEvent(binding: B)
    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
