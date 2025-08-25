package com.nuomi;



import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.nuomi.R;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.ArrayList;
import java.util.List;

public class SessionPickerSheet extends BottomSheetDialogFragment {

    private final List<SessionInfo> items = new ArrayList<>();
    private BaseAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle b) {
        View v = inflater.inflate(R.layout.sheet_session_picker, parent, false);
        ListView list = v.findViewById(R.id.list);
        Context ctx = requireContext();

        items.clear();
        items.addAll(SessionRepo.loadActiveSessions(ctx));

        adapter = new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convertView, ViewGroup parent) {
                View row = convertView != null ? convertView :
                        inflater.inflate(R.layout.row_session_item, parent, false);
                ImageView icon = row.findViewById(R.id.icon);
                TextView title = row.findViewById(R.id.title);
                TextView sub = row.findViewById(R.id.sub);
                SessionInfo si = items.get(i);
                icon.setImageDrawable(si.appIcon);
                title.setText(si.appLabel);
                sub.setText(si.nowPlaying != null ? si.nowPlaying : si.packageName);
                return row;
            }
        };
        list.setAdapter(adapter);

        list.setOnItemClickListener((a, w, pos, id) -> {
            SessionInfo si = items.get(pos);
            // 保存到 SharedPreferences
            requireContext().getSharedPreferences("session_pref", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_pkg", si.packageName)
                    .putString("last_label", si.appLabel)
                    .apply();

            LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(new Intent("com.nuomi.REQUEST_TOKEN"));

            LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(new Intent("com.nuomi.ACTION_SELECTION_CHANGED")
                            .putExtra("pkg", si.packageName)
                            .putExtra("label", si.appLabel));


            SessionDump.dumpFromPicker(requireContext(), si.packageName, si.appLabel);

            Toast.makeText(ctx,
                    "选择了：" + si.appLabel + " (" + si.packageName + ")",
                    Toast.LENGTH_SHORT).show();
            dismiss();
        });

        v.findViewById(R.id.btn_refresh).setOnClickListener(x -> {
            items.clear();
            items.addAll(SessionRepo.loadActiveSessions(ctx));
            adapter.notifyDataSetChanged();
        });
        return v;
    }
}

