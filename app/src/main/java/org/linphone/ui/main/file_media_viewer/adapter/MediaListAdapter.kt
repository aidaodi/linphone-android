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
package org.linphone.ui.main.file_media_viewer.adapter

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.linphone.core.tools.Log
import org.linphone.ui.main.chat.viewmodel.ConversationMediaListViewModel
import org.linphone.ui.main.file_media_viewer.fragment.MediaViewerFragment

class MediaListAdapter(fragment: Fragment, private val viewModel: ConversationMediaListViewModel) : FragmentStateAdapter(
    fragment
) {
    companion object {
        private const val TAG = "[Media List Adapter]"
    }

    override fun getItemCount(): Int {
        return viewModel.mediaList.value.orEmpty().size
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = MediaViewerFragment()
        fragment.arguments = Bundle().apply {
            val path = viewModel.mediaList.value.orEmpty().getOrNull(position)?.file
            Log.i("$TAG Path is [$path] for position [$position]")
            putString("path", path)
        }
        return fragment
    }
}