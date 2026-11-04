/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.setupwizard;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.icu.text.TimeZoneFormat;
import android.icu.text.TimeZoneNames;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.timezone.CountryTimeZones;
import android.timezone.CountryTimeZones.TimeZoneMapping;
import android.timezone.TimeZoneFinder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.DatePicker;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.settingslib.datetime.ZoneGetter;

import org.lineageos.setupwizard.util.SetupWizardUtils;

import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class DateTimeActivity extends BaseSetupWizardActivity implements
        TimePickerDialog.OnTimeSetListener, DatePickerDialog.OnDateSetListener {

    private static final String KEY_ID = "id"; // value: String
    private static final String KEY_DISPLAYNAME = "name"; // value: String
    private static final String KEY_GMT = "gmt"; // value: String
    private static final String KEY_OFFSET = "offset"; // value: int (Integer)

    private TimeZone mCurrentTimeZone;
    private TextView mDateTextView;
    private TextView mTimeTextView;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateTimeAndDateDisplay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setNextText(R.string.next);
        getGlifLayout().setDescriptionText(getString(R.string.date_time_summary));

        final Spinner spinner = findViewById(R.id.timezone_list);
        mCurrentTimeZone = TimeZone.getDefault();
        final SimpleAdapter adapter = constructTimezoneAdapter(this, mCurrentTimeZone);
        View dateView = findViewById(R.id.date_item);
        dateView.setOnClickListener((view) -> showDatePicker());
        View timeView = findViewById(R.id.time_item);
        timeView.setOnClickListener((view) -> showTimePicker());
        mDateTextView = findViewById(R.id.date_text);
        mTimeTextView = findViewById(R.id.time_text);
        // Pre-select current/default timezone
        mHandler.post(() -> {
            int tzIndex = getTimeZoneIndex(adapter, mCurrentTimeZone);
            spinner.setAdapter(adapter);
            if (tzIndex != -1) {
                spinner.setSelection(tzIndex);
            }
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int position,
                        long id) {
                    final Map<?, ?> map = (Map<?, ?>) adapterView.getItemAtPosition(position);
                    final String tzId = (String) map.get(KEY_ID);
                    if (mCurrentTimeZone != null && !mCurrentTimeZone.getID().equals(tzId)) {
                        // Update the system timezone value
                        final AlarmManager alarm =
                                (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                        alarm.setTimeZone(tzId);
                        mCurrentTimeZone = TimeZone.getTimeZone(tzId);
                    }

                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });
        });
        // Pre-select current/default date if epoch
        mHandler.post(() -> {
            final Calendar calendar = Calendar.getInstance();
            final boolean isEpoch = calendar.get(Calendar.YEAR) == 1970;
            if (isEpoch) {
                // If epoch, set date to build date
                long timestamp = SetupWizardUtils.getBuildDateTimestamp();
                if (timestamp > 0) {
                    calendar.setTimeInMillis(timestamp * 1000);
                    setDate(DateTimeActivity.this, calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
                } else {
                    // no build date available, use a sane default
                    setDate(DateTimeActivity.this, 2017, Calendar.JANUARY, 1);
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.setup_datetime_page;
    }

    @Override
    protected int getTitleResId() {
        return R.string.setup_datetime;
    }

    @Override
    protected int getIconResId() {
        return R.drawable.ic_datetime;
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int day) {
        setDate(this, year, month, day);
        updateTimeAndDateDisplay();
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        setTime(this, hourOfDay, minute);
        updateTimeAndDateDisplay();
    }

    private void showDatePicker() {
        DatePickerFragment datePickerFragment = DatePickerFragment.newInstance();
        datePickerFragment.show(getSupportFragmentManager(), DatePickerFragment.TAG);
    }

    private void showTimePicker() {
        TimePickerFragment timePickerFragment = TimePickerFragment.newInstance();
        timePickerFragment.show(getSupportFragmentManager(), TimePickerFragment.TAG);
    }

    private void updateTimeAndDateDisplay() {
        java.text.DateFormat shortDateFormat = DateFormat.getDateFormat(this);
        final Calendar now = Calendar.getInstance();
        mTimeTextView.setText(DateFormat.getTimeFormat(this).format(now.getTime()));
        mDateTextView.setText(shortDateFormat.format(now.getTime()));
    }

    private static SimpleAdapter constructTimezoneAdapter(Context context, TimeZone current) {
        final String[] from = new String[]{KEY_DISPLAYNAME, KEY_GMT};
        final int[] to = new int[]{android.R.id.text1, android.R.id.text2};

        final TimeZoneComparator comparator = new TimeZoneComparator(KEY_OFFSET);
        final List<Map<String, Object>> sortedList = ZoneGetter.getZonesList(context);
        addLocalZones(context, sortedList, current);
        sortedList.sort(comparator);

        return new SimpleAdapter(context,
                sortedList,
                R.layout.date_time_setup_custom_list_item_2,
                from,
                to);
    }

    /**
     * The zones from timezones.xml only cover one city per offset, so add the zones of the
     * country we are in and the current zone, letting the current zone be pre-selected.
     */
    private static void addLocalZones(Context context, List<Map<String, Object>> zones,
            TimeZone current) {
        final Set<String> knownIds = new HashSet<>();
        for (Map<String, Object> zone : zones) {
            knownIds.add((String) zone.get(KEY_ID));
        }

        final long now = System.currentTimeMillis();
        final Set<String> localIds = new LinkedHashSet<>();
        final CountryTimeZones countryZones = TimeZoneFinder.getInstance()
                .lookupCountryTimeZones(getCountryIso(context));
        if (countryZones != null) {
            for (TimeZoneMapping mapping : countryZones.getEffectiveTimeZoneMappingsAt(now)) {
                localIds.add(mapping.getTimeZoneId());
            }
        }
        localIds.add(current.getID());
        localIds.removeAll(knownIds);
        if (localIds.isEmpty()) {
            return;
        }

        final Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        final TimeZoneFormat tzFormatter = TimeZoneFormat.getInstance(locale);
        final TimeZoneNames tzNames = TimeZoneNames.getInstance(locale);
        final Date date = new Date(now);
        for (String id : localIds) {
            final TimeZone tz = TimeZone.getTimeZone(id);
            String name = tzNames.getExemplarLocationName(
                    android.icu.util.TimeZone.getCanonicalID(id));
            if (TextUtils.isEmpty(name)) {
                name = tz.getDisplayName(tz.inDaylightTime(date), TimeZone.LONG, locale);
            }
            final Map<String, Object> zone = new HashMap<>();
            zone.put(KEY_ID, id);
            zone.put(KEY_DISPLAYNAME, name);
            zone.put(KEY_GMT,
                    ZoneGetter.getGmtOffsetText(tzFormatter, locale, tz, date).toString());
            zone.put(KEY_OFFSET, tz.getOffset(now));
            zones.add(zone);
        }
    }

    /** ISO 3166 country code of the network, then of the SIM, then of the locale. */
    private static String getCountryIso(Context context) {
        final TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        String iso = tm != null ? tm.getNetworkCountryIso() : null;
        if (TextUtils.isEmpty(iso) && tm != null) {
            iso = tm.getSimCountryIso();
        }
        if (TextUtils.isEmpty(iso)) {
            iso = context.getResources().getConfiguration().getLocales().get(0).getCountry();
        }
        return iso.toLowerCase(Locale.ROOT);
    }

    private static int getTimeZoneIndex(SimpleAdapter adapter, TimeZone tz) {
        final String defaultId = tz.getID();
        final int listSize = adapter.getCount();
        for (int i = 0; i < listSize; i++) {
            final Map<?, ?> map = (Map<?, ?>) adapter.getItem(i);
            final String id = (String) map.get(KEY_ID);
            if (defaultId.equals(id)) {
                // If current timezone is in this list, move focus to it
                return i;
            }
        }
        return -1;
    }

    private static void setDate(Context context, int year, int month, int day) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        long when = c.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    private static void setTime(Context context, int hourOfDay, int minute) {
        Calendar c = Calendar.getInstance();

        c.set(Calendar.HOUR_OF_DAY, hourOfDay);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long when = c.getTimeInMillis();

        if (when / 1000 < Integer.MAX_VALUE) {
            ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).setTime(when);
        }
    }

    private static class TimeZoneComparator implements Comparator<Map<?, ?>> {
        private final String mSortingKey;

        public TimeZoneComparator(String sortingKey) {
            mSortingKey = sortingKey;
        }

        public int compare(Map<?, ?> map1, Map<?, ?> map2) {
            Object value1 = map1.get(mSortingKey);
            Object value2 = map2.get(mSortingKey);

            /*
             * This should never happen, but just in-case, put non-comparable
             * items at the end.
             */
            if (!isComparable(value1)) {
                return isComparable(value2) ? 1 : 0;
            } else if (!isComparable(value2)) {
                return -1;
            }

            return ((Comparable) value1).compareTo(value2);
        }

        private boolean isComparable(Object value) {
            return (value instanceof Comparable);
        }
    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        private static final String TAG = TimePickerFragment.class.getSimpleName();

        public static TimePickerFragment newInstance() {
            return new TimePickerFragment();
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            ((DateTimeActivity) getActivity()).onTimeSet(view, hourOfDay, minute);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            return new TimePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(getActivity()));
        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        private static final String TAG = DatePickerFragment.class.getSimpleName();

        public static DatePickerFragment newInstance() {
            return new DatePickerFragment();
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int day) {
            ((DateTimeActivity) getActivity()).onDateSet(view, year, month, day);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Calendar calendar = Calendar.getInstance();
            return new DatePickerDialog(
                    getActivity(),
                    this,
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH));
        }
    }
}
