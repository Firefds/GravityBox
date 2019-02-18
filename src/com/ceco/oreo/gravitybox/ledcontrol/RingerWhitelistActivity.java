/*
 * Copyright (C) 2018 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.oreo.gravitybox.ledcontrol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.ceco.oreo.gravitybox.GravityBoxAppCompatActivity;
import com.ceco.oreo.gravitybox.R;
import com.ceco.oreo.gravitybox.ledcontrol.RingerWhitelistActivity.ContactsFragment.SelectionType;

import android.Manifest.permission;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Point;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.text.TextUtils.TruncateAt;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class RingerWhitelistActivity extends GravityBoxAppCompatActivity {

    static final String KEY_SEARCH_QUERY = "searchQuery";
    static final String KEY_SELECTION_TYPE = "selectionType";
    static final String KEY_SELECTED_KEYS = "selectedKeys";

    private String mSearchQuery;
    private SelectionType mSelectionType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, null);
            mSelectionType = SelectionType.valueOf(savedInstanceState.getString(KEY_SELECTION_TYPE, "DEFAULT"));
        }

        setContentView(R.layout.ringer_whitelist_activity);
    }

    private ContactsFragment getFragment() {
        return (ContactsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.contacts_fragment);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        if (mSearchQuery != null) {
            bundle.putString(KEY_SEARCH_QUERY, mSearchQuery);
        }
        if (mSelectionType != null) {
            bundle.putString(KEY_SELECTION_TYPE, mSelectionType.toString());
        }
        super.onSaveInstanceState(bundle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.ringer_whitelist_menu, menu);

        final MenuItem search = menu.findItem(R.id.search);
        final SearchView searchView = (SearchView) search.getActionView();
        final MenuItem searchKeyword = menu.findItem(R.id.searchKeyword);
        final TextView searchKeywordView = (TextView) searchKeyword.getActionView();
        final MenuItem searchReset = menu.findItem(R.id.searchReset);
        final MenuItem showAll = menu.findItem(R.id.showAll);
        final MenuItem showStarred = menu.findItem(R.id.showStarred);
        final MenuItem showWhitelisted = menu.findItem(R.id.showWhitelisted);

        searchView.setOnQueryTextListener(new OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String text) {
                return false;
            }
            @Override
            public boolean onQueryTextSubmit(String text) {
                if (text.trim().length() < 2) {
                    Toast.makeText(RingerWhitelistActivity.this,
                            R.string.search_keyword_short, Toast.LENGTH_SHORT).show();
                } else {
                    mSearchQuery = text.trim();
                    searchView.clearFocus();
                    search.collapseActionView();
                    search.setVisible(false);
                    searchReset.setVisible(true);
                    searchKeyword.setVisible(true);
                    searchKeywordView.setText(mSearchQuery);
                    getFragment().fetchData(mSearchQuery);
                }
                return true;
            }
        });

        searchReset.setOnMenuItemClickListener(new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mSearchQuery = null;
                search.setVisible(true);
                searchReset.setVisible(false);
                searchKeyword.setVisible(false);
                searchKeywordView.setText(null);
                getFragment().fetchData();
                return true;
            }
        });

        searchKeywordView.setSingleLine(true);
        searchKeywordView.setEllipsize(TruncateAt.END);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        searchKeywordView.setMaxWidth(size.x / 3);

        final OnMenuItemClickListener selectionTypeClickListener =
                new OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item == showStarred) {
                    mSelectionType = SelectionType.STARRED;
                    showAll.setEnabled(true);
                    showWhitelisted.setEnabled(true);
                } else if (item == showWhitelisted) {
                    mSelectionType = SelectionType.WHITELISTED;
                    showAll.setEnabled(true);
                    showStarred.setEnabled(true);
                } else {
                    mSelectionType = SelectionType.DEFAULT;
                    showStarred.setEnabled(true);
                    showWhitelisted.setEnabled(true);
                }
                item.setEnabled(false);
                getFragment().setSelectionType(mSelectionType);
                getFragment().fetchData(mSearchQuery);
                return true;
            }
        };
        showAll.setOnMenuItemClickListener(selectionTypeClickListener);
        showAll.setEnabled(mSelectionType != null && mSelectionType != SelectionType.DEFAULT);
        showStarred.setOnMenuItemClickListener(selectionTypeClickListener);
        showStarred.setEnabled(mSelectionType == null || mSelectionType != SelectionType.STARRED);
        showWhitelisted.setOnMenuItemClickListener(selectionTypeClickListener);
        showWhitelisted.setEnabled(mSelectionType == null || mSelectionType != SelectionType.WHITELISTED);

        if (mSearchQuery != null) {
            searchReset.setVisible(true);
            searchKeyword.setVisible(true);
            searchKeywordView.setText(mSearchQuery);
        } else {
            search.setVisible(true);
        }

        return true;
    }

    public static class ContactsFragment extends Fragment
                                  implements LoaderManager.LoaderCallbacks<Cursor>,
                                  AdapterView.OnItemClickListener {

        static enum SelectionType { DEFAULT, STARRED, WHITELISTED };

        private static final String[] FROM_COLUMNS = {
                Contacts.DISPLAY_NAME_PRIMARY,
        };

        private static final int[] TO_IDS = {
                R.id.contactName,
        };

        private static final String[] PROJECTION = {
                Contacts._ID,
                Contacts.LOOKUP_KEY,
                Contacts.DISPLAY_NAME_PRIMARY,
        };

        private static final int LOOKUP_KEY_INDEX = 1;

        private ListView mContactsList;
        private CustomCursorAdapter mCursorAdapter;
        private Set<String> mSelectedKeys = new HashSet<>();
        private String mCurrentQuery;
        private SelectionType mSelectionType;

        public ContactsFragment() { }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            final View layout = inflater.inflate(
                    R.layout.ringer_whitelist_fragment, container, false);
            mContactsList = layout.findViewById(android.R.id.list);
            mContactsList.setOnItemClickListener(this);

            return layout;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            Intent intent = getActivity().getIntent();
            if (savedInstanceState != null && savedInstanceState.containsKey(KEY_SELECTED_KEYS)) {
                mSelectedKeys = new HashSet<String>(
                        savedInstanceState.getStringArrayList(KEY_SELECTED_KEYS));
            } else if (intent != null && intent.hasExtra(QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST)) {
                mSelectedKeys = new HashSet<String>(intent.getStringArrayListExtra(
                        QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST));
            } else {
                mSelectedKeys = new HashSet<String>();
            }
            updateResult();

            mCursorAdapter = new CustomCursorAdapter(
                    getActivity(),
                    R.layout.ringer_whitelist_item,
                    null,
                    FROM_COLUMNS, TO_IDS,
                    0);
            mContactsList.setAdapter(mCursorAdapter);

            String query = null;
            mSelectionType = SelectionType.DEFAULT;
            if (savedInstanceState != null) {
                query = savedInstanceState.getString(KEY_SEARCH_QUERY, null);
                mSelectionType = SelectionType.valueOf(
                        savedInstanceState.getString(KEY_SELECTION_TYPE, "DEFAULT"));
            }
            fetchData(query);
        }

        private void updateResult() {
            getActivity().setResult(Activity.RESULT_OK,
                    (new Intent()).putStringArrayListExtra(QuietHoursActivity.EXTRA_QH_RINGER_WHITELIST,
                            new ArrayList<>(mSelectedKeys)));
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (mCurrentQuery != null) {
                outState.putString(KEY_SEARCH_QUERY, mCurrentQuery);
            }
            if (mSelectionType != null) {
                outState.putString(KEY_SELECTION_TYPE, mSelectionType.toString());
            }
            if (mSelectedKeys != null) {
                outState.putStringArrayList(KEY_SELECTED_KEYS,
                        new ArrayList<>(mSelectedKeys));
            }
            super.onSaveInstanceState(outState);
        }

        private boolean hasContactReadPermission() {
            return getActivity().checkSelfPermission(permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public void onRequestPermissionsResult(int requestCode,
                String[] permissions, int[] grantResults) {
            if (grantResults.length > 0 && grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED) {
                fetchData(mCurrentQuery);
            } else {
                Toast.makeText(getActivity(), R.string.qhrw_permission_denied,
                        Toast.LENGTH_SHORT).show();
            }
        }

        public void fetchData() {
            fetchData(null);
        }

        public void fetchData(String query) {
            mCurrentQuery = query;

            if (!hasContactReadPermission()) {
                requestPermissions(new String[] { permission.READ_CONTACTS }, 0);
                return;
            }

            Bundle args = null;
            if (mCurrentQuery != null) {
                args = new Bundle();
                args.putString(KEY_SEARCH_QUERY, mCurrentQuery);
            }
            getLoaderManager().restartLoader(0, args, this);
        }

        public void setSelectionType(SelectionType type) {
            mSelectionType = type;
        }

        private String createSelection() {
            switch (mSelectionType) {
                default:
                case DEFAULT:
                    return Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? " +
                        "AND " + Contacts.HAS_PHONE_NUMBER + " != 0";
                case STARRED:
                    return Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? " +
                        "AND " + Contacts.STARRED + " = 1 " +
                        "AND " + Contacts.HAS_PHONE_NUMBER + " != 0 ";
                case WHITELISTED:
                    String arg = "";
                    for (int i = 0; i < mSelectedKeys.size(); i++) {
                        if (arg != "") arg += ",";
                        arg += "?";
                    }
                    return Contacts.DISPLAY_NAME_PRIMARY + " LIKE ? " +
                            "AND " + Contacts.LOOKUP_KEY + " IN (" + arg + ")";
            }
        }

        private String[] createSelectionArgs(String query) {
            switch (mSelectionType) {
                default:
                case DEFAULT:
                case STARRED:
                    return new String[] { query == "%" ? query : "%" + query + "%" };
                case WHITELISTED:
                    String args[] = new String[mSelectedKeys.size()+1];
                    args[0] = query == "%" ? query : "%" + query + "%";
                    int i = 1;
                    for (String key : mSelectedKeys) {
                        args[i++] = key;
                    }
                    return args;
            }
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            String query = "%";
            if (args != null && args.containsKey(KEY_SEARCH_QUERY)) {
                query = args.getString(KEY_SEARCH_QUERY);
            }

            return new CursorLoader(
                    getActivity(),
                    Contacts.CONTENT_URI,
                    PROJECTION,
                    createSelection(),
                    createSelectionArgs(query),
                    Contacts.DISPLAY_NAME_PRIMARY
            );
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mCursorAdapter.swapCursor(data);
            if (data.getCount() == 0) {
                Toast.makeText(getActivity(), R.string.search_no_contacts,
                        Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mCursorAdapter.swapCursor(null);
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Cursor cursor = ((CustomCursorAdapter) parent.getAdapter()).getCursor();
            cursor.moveToPosition(position);
            String contactKey = cursor.getString(LOOKUP_KEY_INDEX);

            CheckedTextView cb = view.findViewById(R.id.contactName);
            if (!cb.isChecked()) {
                cb.setChecked(true);
                mSelectedKeys.add(contactKey);
            } else {
                cb.setChecked(false);
                mSelectedKeys.remove(contactKey);
            }
            updateResult();
        }

        private class CustomCursorAdapter extends SimpleCursorAdapter {

            public CustomCursorAdapter(Context context, int layout, Cursor c,
                    String[] from, int[] to, int flags) {
                super(context, layout, c, from, to, flags);
            }

            @Override
            public void bindView(View view, Context context, Cursor cursor) {
                super.bindView(view, context, cursor);

                String key = cursor.getString(LOOKUP_KEY_INDEX);
                CheckedTextView nameView = (CheckedTextView)view.findViewById(R.id.contactName);
                nameView.setChecked(mSelectedKeys.contains(key));
            }
        }
    }
}
