package com.example.sha7na;

import android.content.Context;
import android.telephony.SubscriptionInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

class SimAdapter extends ArrayAdapter<SubscriptionInfo> {
    public SimAdapter(@NonNull Context context, @NonNull List<SubscriptionInfo> sims) {
        super(context, 0, sims);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_list_item, parent, false);
        }

        SubscriptionInfo info = getItem(position);

        TextView simLabel = convertView.findViewById(R.id.sim_label);
        TextView simInfo = convertView.findViewById(R.id.sim_info);

        // SIM label text (dynamic)
        simLabel.setText("SIM " + (info.getSimSlotIndex() + 1));

        simInfo.setText(info.getCarrierName());
        simLabel.setBackgroundResource(R.drawable.bg_sim_label);

        return convertView;
    }
}
