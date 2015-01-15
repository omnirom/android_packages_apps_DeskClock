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
    private List<File> mSubDirs;
    private ArrayAdapter<File> mListAdapter;
    private ListView mListView;
    private String mTag;
    private TextView mCurrentDirDisplay;

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

    private List<File> getDirectories(String dir) {
        List<File> dirs = new ArrayList<File>();

        try {
            File dirFile = new File(dir);
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            for (File file : dirFile.listFiles()) {
                if (!file.getName().startsWith(".")) {
                    dirs.add(file);
                }
            }
        } catch (Exception e) {
        }

        Collections.sort(dirs, new Comparator<File>() {
            public int compare(File o1, File o2) {
                if (o1.isDirectory() && o2.isFile()) {
                    return -1;
                }
                if (o2.isDirectory() && o1.isFile()) {
                    return 1;
                }
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });
        if (!dir.equals(mSDCardDirectory)) {
            dirs.add(0, new File(".."));
        }
        return dirs;
    }

    private void updateDirectory() {
        mSubDirs.clear();
        mSubDirs.addAll(getDirectories(mCurrentDir));
        mCurrentDirDisplay.setText(mCurrentDir);
        mListAdapter.notifyDataSetChanged();
    }

    private ArrayAdapter<File> createListAdapter(List<File> items) {
        return new ArrayAdapter<File>(getActivity(),
                R.layout.folder_item, R.id.folder_name, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View item = null;
                if (convertView == null){
                    final LayoutInflater inflater = (LayoutInflater) getActivity()
                            .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    item = inflater.inflate(R.layout.folder_item, null);
                } else {
                    item = convertView;
                }
                TextView tv = (TextView) item.findViewById(R.id.folder_name);
                File f = mSubDirs.get(position);
                tv.setText(f.getName());
                if (f.isFile()) {
                    tv.setTextColor(getActivity().getResources().getColor(R.color.clock_gray));
                } else {
                    tv.setTextColor(getActivity().getResources().getColor(R.color.black_87p));
                }
                return item;
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
                File f = mListAdapter.getItem(position);
                if (f.isFile()) {
                    return;
                }
                if (f.getName().equals("..")) {
                    if (!mCurrentDir.equals(mSDCardDirectory)) {
                        // Navigate back to an upper directory
                        mCurrentDir = new File(mCurrentDir).getParent();
                    }
                } else {
                    // Navigate into the sub-directory
                    mCurrentDir = mListAdapter.getItem(position).getAbsolutePath();
                }
                updateDirectory();
            }
        });
        mCurrentDirDisplay = (TextView) view.findViewById(R.id.current_folder);
        mCurrentDirDisplay.setText(mCurrentDir);
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
