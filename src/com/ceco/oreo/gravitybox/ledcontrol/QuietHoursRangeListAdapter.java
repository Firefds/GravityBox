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

import java.util.List;

import com.ceco.oreo.gravitybox.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class QuietHoursRangeListAdapter extends ArrayAdapter<QuietHoursRangeListItem> {

    private Context mContext;
    private List<QuietHoursRangeListItem> mData = null;
    private ListItemActionHandler mActionHandler;

    protected interface ListItemActionHandler {
        void onItemDeleted(QuietHoursRangeListItem item);
    }

    protected QuietHoursRangeListAdapter(Context context, List<QuietHoursRangeListItem> objects,ListItemActionHandler handler) {
        super(context, R.layout.quiet_hours_range_list_item, objects);

        mContext = context;
        mData = objects;
        mActionHandler = handler;
    }

    static class ViewHolder {
        TextView nameView;
        TextView descView;
        ImageView deleteView;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        ViewHolder holder = null;

        if(row == null) {
            LayoutInflater inflater =
                    (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            row = inflater.inflate(R.layout.quiet_hours_range_list_item, parent, false);

            holder = new ViewHolder();
            holder.nameView = (TextView) row.findViewById(R.id.name);
            holder.descView = (TextView) row.findViewById(R.id.desc);
            holder.deleteView = (ImageView) row.findViewById(R.id.btnDelete);
            holder.deleteView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mActionHandler != null) {
                        mActionHandler.onItemDeleted((QuietHoursRangeListItem) v.getTag());
                    }
                }
            });

            row.setTag(holder);
        } else {
            holder = (ViewHolder) row.getTag();
        }

        QuietHoursRangeListItem item = mData.get(position);
        holder.nameView.setText(item.getText());
        holder.descView.setText(item.getSubText());
        holder.deleteView.setTag(item);

        return row;
    }
}
