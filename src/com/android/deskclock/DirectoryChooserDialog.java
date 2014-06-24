/*
 *  Copyright (C) 2014 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.android.deskclock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class DirectoryChooserDialog extends DialogFragment
    implements DialogInterface.OnClickListener {

    private static final String KEY_TAG = "tag";

    private String mSDCardDirectory;
    private String mCurrentDir;
    private List<String> mSubDirs;
    private ArrayAdapter<String> mListAdapter;
    private ListView mListView;
    private String mTag;

    public interface ChosenDirectoryListener {
        public void onChooseDirOk(Uri chosenDir);

        public void onChooseDirCancel();
    }

    public static DirectoryChooserDialog newInstance(String tag) {
        DirectoryChooserDialog fragment = new DirectoryChooserDialog();
        Bundle args = new Bundle();
        args.putString(KEY_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    public DirectoryChooserDialog() {
        mSDCardDirectory = Environment.getExternalStorageDirectory()
                .getAbsolutePath();
        try {
            mSDCardDirectory = new File(mSDCardDirectory).getCanonicalPath();
        } catch (IOException ioe) {
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        mTag = bundle.getString(KEY_TAG);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.folder_dialog_title)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setView(createDialogView());

        return builder.create();
    }

    private List<String> getDirectories(String dir) {
        List<String> dirs = new ArrayList<String>();

        try {
            File dirFile = new File(dir);
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            for (File file : dirFile.listFiles()) {
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    dirs.add(file.getName());
                }
            }
        } catch (Exception e) {
        }

        Collections.sort(dirs, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        });
        if (!dir.equals(mSDCardDirectory)) {
            dirs.add(0, "..");
        }
        return dirs;
    }

    private void updateDirectory() {
        mSubDirs.clear();
        mSubDirs.addAll(getDirectories(mCurrentDir));
        mListAdapter.notifyDataSetChanged();
    }

    private ArrayAdapter<String> createListAdapter(List<String> items) {
        return new ArrayAdapter<String>(getActivity(),
                R.layout.folder_item, R.id.folder_name, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);

                if (v instanceof TextView) {
                    // Enable list item (directory) text wrapping
                    TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }

    private View createDialogView() {
        final LayoutInflater inflater = (LayoutInflater) getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final View view = inflater
                .inflate(R.layout.folder_dialog, null);

        mCurrentDir = mSDCardDirectory;
        mSubDirs = getDirectories(mSDCardDirectory);

        mListAdapter = createListAdapter(mSubDirs);
        mListView = (ListView) view.findViewById(R.id.folders);
        mListView.setAdapter(mListAdapter);

        mListView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                if (mListAdapter.getItem(position).equals("..")) {
                    if (!mCurrentDir.equals(mSDCardDirectory)) {
                        // Navigate back to an upper directory
                        mCurrentDir = new File(mCurrentDir).getParent();
                    }
                } else {
                    // Navigate into the sub-directory
                    mCurrentDir = new File(mCurrentDir, mListAdapter.getItem(position)).getAbsolutePath();
                }
                updateDirectory();
            }
        });
        return view;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            Uri uri = Uri.fromFile(new File(mCurrentDir));
            Fragment frag = getFragmentManager().findFragmentByTag(mTag);
            if (frag instanceof ChosenDirectoryListener) {
                ((ChosenDirectoryListener) frag).onChooseDirOk(uri);
            }
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            Fragment frag = getFragmentManager().findFragmentByTag(mTag);
            if (frag instanceof ChosenDirectoryListener) {
                ((ChosenDirectoryListener) frag).onChooseDirCancel();
            }
        }
    }
}
