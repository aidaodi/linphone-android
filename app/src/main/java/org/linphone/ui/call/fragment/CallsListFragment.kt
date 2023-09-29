/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.call.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import org.linphone.core.tools.Log
import org.linphone.databinding.CallsListFragmentBinding
import org.linphone.ui.call.adapter.CallsListAdapter
import org.linphone.ui.call.viewmodel.CallsViewModel

class CallsListFragment : GenericCallFragment() {
    companion object {
        private const val TAG = "[Calls List Fragment]"
    }

    private lateinit var binding: CallsListFragmentBinding

    private lateinit var viewModel: CallsViewModel

    private lateinit var adapter: CallsListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = CallsListFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = requireActivity().run {
            ViewModelProvider(this)[CallsViewModel::class.java]
        }

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        adapter = CallsListAdapter(viewLifecycleOwner)
        binding.callsList.setHasFixedSize(true)
        binding.callsList.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        binding.callsList.layoutManager = layoutManager

        adapter.callLongClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                val modalBottomSheet = CallMenuDialogFragment(model) {
                    // onDismiss
                    adapter.resetSelection()
                }
                modalBottomSheet.show(parentFragmentManager, CallMenuDialogFragment.TAG)
            }
        }

        adapter.callClickedEvent.observe(viewLifecycleOwner) {
            it.consume { model ->
                model.togglePauseResume()
            }
        }

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        viewModel.calls.observe(viewLifecycleOwner) {
            Log.i("$TAG Calls list updated with [${it.size}] items")
            adapter.submitList(it)
        }
    }
}