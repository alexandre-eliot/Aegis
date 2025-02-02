package com.beemdevelopment.aegis.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;

import com.beemdevelopment.aegis.AccountNamePosition;
import com.beemdevelopment.aegis.CopyBehavior;
import com.beemdevelopment.aegis.Preferences;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.SortCategory;
import com.beemdevelopment.aegis.ViewMode;
import com.beemdevelopment.aegis.helpers.FabScrollHelper;
import com.beemdevelopment.aegis.helpers.PermissionHelper;
import com.beemdevelopment.aegis.otp.GoogleAuthInfo;
import com.beemdevelopment.aegis.otp.GoogleAuthInfoException;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.ui.dialogs.Dialogs;
import com.beemdevelopment.aegis.ui.fragments.preferences.BackupsPreferencesFragment;
import com.beemdevelopment.aegis.ui.fragments.preferences.PreferencesFragment;
import com.beemdevelopment.aegis.ui.models.ErrorCardInfo;
import com.beemdevelopment.aegis.ui.tasks.QrDecodeTask;
import com.beemdevelopment.aegis.ui.views.EntryListView;
import com.beemdevelopment.aegis.util.TimeUtils;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultFile;
import com.beemdevelopment.aegis.vault.VaultRepository;
import com.beemdevelopment.aegis.vault.VaultRepositoryException;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.base.Strings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MainActivity extends AegisActivity implements EntryListView.Listener {
    // Permission request codes
    private static final int CODE_PERM_CAMERA = 0;

    private boolean _loaded;
    private boolean _isRecreated;
    private boolean _isDPadPressed;
    private boolean _isDoingIntro;
    private boolean _isAuthenticating;

    private String _submittedSearchQuery;
    private String _pendingSearchQuery;

    private List<VaultEntry> _selectedEntries;

    private Menu _menu;
    private SearchView _searchView;
    private EntryListView _entryListView;

    private FabScrollHelper _fabScrollHelper;

    private ActionMode _actionMode;
    private ActionMode.Callback _actionModeCallbacks = new ActionModeCallbacks();

    private LockBackPressHandler _lockBackPressHandler;
    private SearchViewBackPressHandler _searchViewBackPressHandler;
    private ActionModeBackPressHandler _actionModeBackPressHandler;

    private final ActivityResultLauncher<Intent> authResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                _isAuthenticating = false;
                if (activityResult.getResultCode() == RESULT_OK) {
                    onDecryptResult();
                }
            });

    private final ActivityResultLauncher<Intent> introResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                _isDoingIntro = false;
                if (activityResult.getResultCode() == RESULT_OK) {
                    onIntroResult();
                }
            });

    private final ActivityResultLauncher<Intent> scanResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onScanResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> assignIconsResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onAssignEntriesResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> preferenceResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onPreferencesResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> editEntryResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onEditEntryResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> addEntryResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() != RESULT_OK || activityResult.getData() == null) {
                    return;
                }
                onAddEntryResult(activityResult.getData());
            });

    private final ActivityResultLauncher<Intent> codeScanResultLauncher =
            registerForActivityResult(new StartActivityForResult(), activityResult -> {
                if (activityResult.getResultCode() == RESULT_OK && activityResult.getData() != null) {
                    onScanImageResult(activityResult.getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        _loaded = false;
        _isDPadPressed = false;
        _isDoingIntro = false;
        _isAuthenticating = false;
        if (savedInstanceState != null) {
            _isRecreated = true;
            _pendingSearchQuery = savedInstanceState.getString("pendingSearchQuery");
            _submittedSearchQuery = savedInstanceState.getString("submittedSearchQuery");
            _isDoingIntro = savedInstanceState.getBoolean("isDoingIntro");
            _isAuthenticating = savedInstanceState.getBoolean("isAuthenticating");
        }

        _lockBackPressHandler = new LockBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _lockBackPressHandler);
        _searchViewBackPressHandler = new SearchViewBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _searchViewBackPressHandler);
        _actionModeBackPressHandler = new ActionModeBackPressHandler();
        getOnBackPressedDispatcher().addCallback(this, _actionModeBackPressHandler);

        _entryListView = (EntryListView) getSupportFragmentManager().findFragmentById(R.id.key_profiles);
        _entryListView.setListener(this);
        _entryListView.setCodeGroupSize(_prefs.getCodeGroupSize());
        _entryListView.setAccountNamePosition(_prefs.getAccountNamePosition());
        _entryListView.setShowIcon(_prefs.isIconVisible());
        _entryListView.setOnlyShowNecessaryAccountNames(_prefs.onlyShowNecessaryAccountNames());
        _entryListView.setHighlightEntry(_prefs.isEntryHighlightEnabled());
        _entryListView.setPauseFocused(_prefs.isPauseFocusedEnabled());
        _entryListView.setTapToReveal(_prefs.isTapToRevealEnabled());
        _entryListView.setTapToRevealTime(_prefs.getTapToRevealTime());
        _entryListView.setSortCategory(_prefs.getCurrentSortCategory(), false);
        _entryListView.setViewMode(_prefs.getCurrentViewMode());
        _entryListView.setCopyBehavior(_prefs.getCopyBehavior());
        _entryListView.setPrefGroupFilter(_prefs.getGroupFilter());

         FloatingActionButton fab = findViewById(R.id.fab);
         fab.setOnClickListener(v -> {
             View view = getLayoutInflater().inflate(R.layout.dialog_add_entry, null);
             BottomSheetDialog dialog = new BottomSheetDialog(this);
             dialog.setContentView(view);

             view.findViewById(R.id.fab_enter).setOnClickListener(v1 -> {
                 dialog.dismiss();
                 startEditEntryActivityForManual();
             });
             view.findViewById(R.id.fab_scan_image).setOnClickListener(v2 -> {
                 dialog.dismiss();
                 startScanImageActivity();
             });
             view.findViewById(R.id.fab_scan).setOnClickListener(v3 -> {
                 dialog.dismiss();
                 startScanActivity();
             });

             Dialogs.showSecureDialog(dialog);
         });

        _fabScrollHelper = new FabScrollHelper(fab);
        _selectedEntries = new ArrayList<>();
    }

    @Override
    protected void onDestroy() {
        _entryListView.setListener(null);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Map<UUID, Integer> usageMap = _entryListView.getUsageCounts();
        if (usageMap != null) {
            _prefs.setUsageCount(usageMap);
        }

        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle instance) {
        super.onSaveInstanceState(instance);
        instance.putString("pendingSearchQuery", _pendingSearchQuery);
        instance.putString("submittedSearchQuery", _submittedSearchQuery);
        instance.putBoolean("isDoingIntro", _isDoingIntro);
        instance.putBoolean("isAuthenticating", _isAuthenticating);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (!PermissionHelper.checkResults(grantResults)) {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestCode == CODE_PERM_CAMERA) {
            startScanActivity();
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        _isDPadPressed = isDPadKey(keyCode);
        return super.onKeyDown(keyCode, event);
    }

    private static boolean isDPadKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    @Override
    public void onEntryListTouch() {
        _isDPadPressed = false;
    }

    private void onPreferencesResult(Intent data) {
        // refresh the entire entry list if needed
        if (_loaded) {
            if (data.getBooleanExtra("needsRecreate", false)) {
                recreate();
            } else if (data.getBooleanExtra("needsRefresh", false)) {
                AccountNamePosition accountNamePosition = _prefs.getAccountNamePosition();
                boolean showIcons = _prefs.isIconVisible();
                boolean onlyShowNecessaryAccountNames = _prefs.onlyShowNecessaryAccountNames();
                Preferences.CodeGrouping codeGroupSize = _prefs.getCodeGroupSize();
                boolean highlightEntry = _prefs.isEntryHighlightEnabled();
                boolean pauseFocused = _prefs.isPauseFocusedEnabled();
                boolean tapToReveal = _prefs.isTapToRevealEnabled();
                int tapToRevealTime = _prefs.getTapToRevealTime();
                ViewMode viewMode = _prefs.getCurrentViewMode();
                CopyBehavior copyBehavior = _prefs.getCopyBehavior();
                _entryListView.setAccountNamePosition(accountNamePosition);
                _entryListView.setOnlyShowNecessaryAccountNames(onlyShowNecessaryAccountNames);
                _entryListView.setShowIcon(showIcons);
                _entryListView.setCodeGroupSize(codeGroupSize);
                _entryListView.setHighlightEntry(highlightEntry);
                _entryListView.setPauseFocused(pauseFocused);
                _entryListView.setTapToReveal(tapToReveal);
                _entryListView.setTapToRevealTime(tapToRevealTime);
                _entryListView.setViewMode(viewMode);
                _entryListView.setCopyBehavior(copyBehavior);
                _entryListView.refresh(true);
            }
        }
    }

    private void startEditEntryActivityForNew(VaultEntry entry) {
        Intent intent = new Intent(this, EditEntryActivity.class);
        intent.putExtra("newEntry", entry);
        intent.putExtra("isManual", false);
        addEntryResultLauncher.launch(intent);
    }

    private void startEditEntryActivityForManual() {
        Intent intent = new Intent(this, EditEntryActivity.class);
        intent.putExtra("newEntry", VaultEntry.getDefault());
        intent.putExtra("isManual", true);
        addEntryResultLauncher.launch(intent);
    }

    private void startEditEntryActivity(VaultEntry entry) {
        Intent intent = new Intent(this, EditEntryActivity.class);
        intent.putExtra("entryUUID", entry.getUUID());
        editEntryResultLauncher.launch(intent);
    }

    private void startAssignIconsActivity(List<VaultEntry> entries) {
        ArrayList<UUID> assignIconEntriesIds = new ArrayList<>();
        Intent assignIconIntent = new Intent(getBaseContext(), AssignIconsActivity.class);
        for (VaultEntry entry : entries) {
            assignIconEntriesIds.add(entry.getUUID());
        }

        assignIconIntent.putExtra("entries", assignIconEntriesIds);
        assignIconsResultLauncher.launch(assignIconIntent);
    }

    private void startIntroActivity() {
        if (!_isDoingIntro) {
            Intent intro = new Intent(this, IntroActivity.class);
            introResultLauncher.launch(intro);
            _isDoingIntro = true;
        }
    }

    private void onScanResult(Intent data) {
        List<VaultEntry> entries = (ArrayList<VaultEntry>) data.getSerializableExtra("entries");
        if (entries != null) {
            importScannedEntries(entries);
        }
    }

    private void onAddEntryResult(Intent data) {
        if (_loaded) {
            UUID entryUUID = (UUID) data.getSerializableExtra("entryUUID");
            VaultEntry entry = _vaultManager.getVault().getEntryByUUID(entryUUID);
            _entryListView.addEntry(entry, true);
        }
    }

    private void onEditEntryResult(Intent data) {
        if (_loaded) {
            UUID entryUUID = (UUID) data.getSerializableExtra("entryUUID");

            if (data.getBooleanExtra("delete", false)) {
                _entryListView.removeEntry(entryUUID);
            } else {
                VaultEntry entry = _vaultManager.getVault().getEntryByUUID(entryUUID);
                _entryListView.replaceEntry(entryUUID, entry);
            }
        }
    }

    private void onAssignEntriesResult(Intent data) {
        if (_loaded) {
            ArrayList<UUID> entryUUIDs = (ArrayList<UUID>) data.getSerializableExtra("entryUUIDs");

            for (UUID entryUUID: entryUUIDs) {
                VaultEntry entry = _vaultManager.getVault().getEntryByUUID(entryUUID);
                _entryListView.replaceEntry(entryUUID, entry);
            }
        }
    }

    private void onScanImageResult(Intent intent) {
        if (intent.getData() != null) {
            startDecodeQrCodeImages(Collections.singletonList(intent.getData()));
            return;
        }

        if (intent.getClipData() != null) {
            ClipData data = intent.getClipData();

            List<Uri> uris = new ArrayList<>();
            for (int i = 0; i < data.getItemCount(); i++) {
                ClipData.Item item = data.getItemAt(i);
                if (item.getUri() != null) {
                    uris.add(item.getUri());
                }
            }

            if (uris.size() > 0) {
                startDecodeQrCodeImages(uris);
            }
        }
    }

    private static CharSequence buildImportError(String fileName, Throwable e) {
        SpannableStringBuilder builder = new SpannableStringBuilder(String.format("%s:\n%s", fileName, e));
        builder.setSpan(new StyleSpan(Typeface.BOLD), 0, fileName.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private void startDecodeQrCodeImages(List<Uri> uris) {
        QrDecodeTask task = new QrDecodeTask(this, (results) -> {
            List<CharSequence> errors = new ArrayList<>();
            List<VaultEntry> entries = new ArrayList<>();
            List<GoogleAuthInfo.Export> googleAuthExports = new ArrayList<>();

            for (QrDecodeTask.Result res : results) {
                if (res.getException() != null) {
                    errors.add(buildImportError(res.getFileName(), res.getException()));
                    continue;
                }

                try {
                    Uri scanned = Uri.parse(res.getResult().getText());
                    if (Objects.equals(scanned.getScheme(), GoogleAuthInfo.SCHEME_EXPORT)) {
                        GoogleAuthInfo.Export export = GoogleAuthInfo.parseExportUri(scanned);
                        for (GoogleAuthInfo info: export.getEntries()) {
                            VaultEntry entry = new VaultEntry(info);
                            entries.add(entry);
                        }
                        googleAuthExports.add(export);
                    } else {
                        GoogleAuthInfo info = GoogleAuthInfo.parseUri(res.getResult().getText());
                        VaultEntry entry = new VaultEntry(info);
                        entries.add(entry);
                    }
                } catch (GoogleAuthInfoException e) {
                    errors.add(buildImportError(res.getFileName(), e));
                }
            }

            final DialogInterface.OnClickListener dialogDismissHandler = (dialog, which) -> importScannedEntries(entries);
            if (!googleAuthExports.isEmpty()) {
                boolean isSingleBatch = GoogleAuthInfo.Export.isSingleBatch(googleAuthExports);
                if (!isSingleBatch && errors.size() > 0) {
                    errors.add(getString(R.string.unrelated_google_auth_batches_error));
                    Dialogs.showMultiErrorDialog(this, R.string.import_error_title, getString(R.string.no_tokens_can_be_imported), errors, null);
                    return;
                } else if (!isSingleBatch) {
                    Dialogs.showErrorDialog(this, R.string.import_google_auth_failure, getString(R.string.unrelated_google_auth_batches_error));
                    return;
                } else {
                    List<Integer> missingIndices = GoogleAuthInfo.Export.getMissingIndices(googleAuthExports);
                    if (missingIndices.size() != 0) {
                        Dialogs.showPartialGoogleAuthImportWarningDialog(this, missingIndices, entries.size(), errors, dialogDismissHandler);
                        return;
                    }
                }
            }

            if ((errors.size() > 0 && results.size() > 1) || errors.size() > 1) {
                Dialogs.showMultiErrorDialog(this, R.string.import_error_title, getString(R.string.unable_to_read_qrcode_files, uris.size() - errors.size(), uris.size()), errors, dialogDismissHandler);
            } else if (errors.size() > 0) {
                Dialogs.showErrorDialog(this, getString(R.string.unable_to_read_qrcode_file, results.get(0).getFileName()), errors.get(0), dialogDismissHandler);
            } else {
                importScannedEntries(entries);
            }
        });
        task.execute(getLifecycle(), uris);
    }

    private void importScannedEntries(List<VaultEntry> entries) {
        if (entries.size() == 1) {
            startEditEntryActivityForNew(entries.get(0));
        } else if (entries.size() > 1) {
            for (VaultEntry entry: entries) {
                _vaultManager.getVault().addEntry(entry);
                _entryListView.addEntry(entry);
            }

            if (saveAndBackupVault()) {
                Toast.makeText(this, getResources().getQuantityString(R.plurals.added_new_entries, entries.size(), entries.size()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateSortCategoryMenu() {
        SortCategory category = _prefs.getCurrentSortCategory();
        _menu.findItem(category.getMenuItem()).setChecked(true);
    }

    private void onIntroResult() {
        loadEntries();
        checkTimeSyncSetting();
    }

    private void checkTimeSyncSetting() {
        boolean autoTime = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 1) == 1;
        if (!autoTime && _prefs.isTimeSyncWarningEnabled()) {
            Dialogs.showTimeSyncWarningDialog(this, (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_DATE_SETTINGS);
                startActivity(intent);
            });
        }
    }

    private void onDecryptResult() {
        loadEntries();
        checkTimeSyncSetting();
    }

    private void startScanActivity() {
        if (!PermissionHelper.request(this, CODE_PERM_CAMERA, Manifest.permission.CAMERA)) {
            return;
        }

        Intent scannerActivity = new Intent(getApplicationContext(), ScannerActivity.class);
        scanResultLauncher.launch(scannerActivity);
    }

    private void startScanImageActivity() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        fileIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.select_picture));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { fileIntent });
        _vaultManager.fireIntentLauncher(this, chooserIntent, codeScanResultLauncher);
    }

    private void startPreferencesActivity() {
        startPreferencesActivity(null, null);
    }

    private void startPreferencesActivity(Class<? extends PreferencesFragment> fragmentType, String preference) {
        Intent intent = new Intent(this, PreferencesActivity.class);
        intent.putExtra("fragment", fragmentType);
        intent.putExtra("pref", preference);
        preferenceResultLauncher.launch(intent);
    }

    private void doShortcutActions() {
        Intent intent = getIntent();
        String action = intent.getStringExtra("action");
        if (action == null || !_vaultManager.isVaultLoaded()) {
            return;
        }

        switch (action) {
            case "scan":
                startScanActivity();
                break;
        }

        intent.removeExtra("action");
    }

    private void handleIncomingIntent() {
        if (!_vaultManager.isVaultLoaded()) {
            return;
        }

        Intent intent = getIntent();
        if (intent.getAction() == null) {
            return;
        }

        Uri uri;
        switch (intent.getAction()) {
            case Intent.ACTION_VIEW:
                uri = intent.getData();
                if (uri != null) {
                    intent.setData(null);
                    intent.setAction(null);

                    GoogleAuthInfo info;
                    try {
                        info = GoogleAuthInfo.parseUri(uri);
                    } catch (GoogleAuthInfoException e) {
                        e.printStackTrace();
                        Dialogs.showErrorDialog(this, R.string.unable_to_process_deeplink, e);
                        break;
                    }

                    VaultEntry entry = new VaultEntry(info);
                    startEditEntryActivityForNew(entry);
                }
                break;
            case Intent.ACTION_SEND:
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    intent.setAction(null);
                    intent.removeExtra(Intent.EXTRA_STREAM);

                    if (uri != null) {
                        startDecodeQrCodeImages(Collections.singletonList(uri));
                    }
                }
                if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                    String stringExtra = intent.getStringExtra(Intent.EXTRA_TEXT);
                    intent.setAction(null);
                    intent.removeExtra(Intent.EXTRA_TEXT);

                    if (stringExtra != null) {
                        GoogleAuthInfo info;
                        try {
                            info = GoogleAuthInfo.parseUri(stringExtra);
                        } catch (GoogleAuthInfoException e) {
                            Dialogs.showErrorDialog(this, R.string.unable_to_process_shared_text, e);
                            break;
                        }

                        VaultEntry entry = new VaultEntry(info);
                        startEditEntryActivityForNew(entry);
                    }
                }
                break;
            case Intent.ACTION_SEND_MULTIPLE:
                if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                    List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    intent.setAction(null);
                    intent.removeExtra(Intent.EXTRA_STREAM);

                    if (uris != null) {
                        uris = uris.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());

                        startDecodeQrCodeImages(uris);
                    }
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (_vaultManager.isVaultInitNeeded()) {
            if (_prefs.isIntroDone()) {
                Toast.makeText(this, getString(R.string.vault_not_found), Toast.LENGTH_SHORT).show();
            }
            startIntroActivity();
            return;
        }

        // If the vault is not loaded yet, try to load it now in case it's plain text
        if (!_vaultManager.isVaultLoaded()) {
            VaultFile vaultFile;
            try {
                vaultFile = VaultRepository.readVaultFile(this);
            } catch (VaultRepositoryException e) {
                e.printStackTrace();
                Dialogs.showErrorDialog(this, R.string.vault_load_error, e, (dialog, which) -> {
                    finish();
                });
                return;
            }

            if (!vaultFile.isEncrypted()) {
                try {
                    _vaultManager.loadFrom(vaultFile);
                } catch (VaultRepositoryException e) {
                    e.printStackTrace();
                    Dialogs.showErrorDialog(this, R.string.vault_load_error, e, (dialog, which) -> {
                        finish();
                    });
                    return;
                }
            }
        }

        if (!_vaultManager.isVaultLoaded()) {
            startAuthActivity(false);
        } else if (_loaded) {
            // update the list of groups in the entry list view so that the chip gets updated
            _entryListView.setGroups(_vaultManager.getVault().getUsedGroups());

            // update the usage counts in case they are edited outside of the EntryListView
            _entryListView.setUsageCounts(_prefs.getUsageCounts());

            // refresh all codes to prevent showing old ones
            _entryListView.refresh(false);
        } else {
            loadEntries();
            checkTimeSyncSetting();
        }

        _lockBackPressHandler.setEnabled(
                _vaultManager.isAutoLockEnabled(Preferences.AUTO_LOCK_ON_BACK_BUTTON)
        );

        handleIncomingIntent();
        updateLockIcon();
        doShortcutActions();
        updateErrorCard();
    }

    private void deleteEntries(List<VaultEntry> entries) {
        for (VaultEntry entry: entries) {
            VaultEntry oldEntry = _vaultManager.getVault().removeEntry(entry);
            _entryListView.removeEntry(oldEntry);
        }

        saveAndBackupVault();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        _menu = menu;
        getMenuInflater().inflate(R.menu.menu_main, menu);

        updateLockIcon();
        if (_loaded) {
            _entryListView.setGroups(_vaultManager.getVault().getUsedGroups());
            updateSortCategoryMenu();
        }

        MenuItem searchViewMenuItem = menu.findItem(R.id.mi_search);

        _searchView = (SearchView) searchViewMenuItem.getActionView();
        _searchView.setMaxWidth(Integer.MAX_VALUE);
        _searchView.setOnQueryTextFocusChangeListener((v, hasFocus) -> {
            boolean enabled = _submittedSearchQuery != null || hasFocus;
            _searchViewBackPressHandler.setEnabled(enabled);
        });
        _searchView.setOnCloseListener(() -> {
            boolean enabled = _submittedSearchQuery != null;
            _searchViewBackPressHandler.setEnabled(enabled);
            return false;
        });

        _searchView.setQueryHint(getString(R.string.search));
        _searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                setTitle(getString(R.string.search));
                getSupportActionBar().setSubtitle(s);
                _entryListView.setSearchFilter(s);
                _pendingSearchQuery = null;
                _submittedSearchQuery = s;
                collapseSearchView();
                _searchViewBackPressHandler.setEnabled(true);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (_submittedSearchQuery == null) {
                    _entryListView.setSearchFilter(s);
                }

                _pendingSearchQuery = Strings.isNullOrEmpty(s) && !_searchView.isIconified() ? null : s;
                if (_pendingSearchQuery != null) {
                    _entryListView.setSearchFilter(_pendingSearchQuery);
                }

                return false;
            }
        });
        _searchView.setOnSearchClickListener(v -> {
            String query = _submittedSearchQuery != null ? _submittedSearchQuery : _pendingSearchQuery;
            _searchView.setQuery(query, false);
        });

        if (_pendingSearchQuery != null) {
            _searchView.setIconified(false);
            _searchView.setQuery(_pendingSearchQuery, false);
            _searchViewBackPressHandler.setEnabled(true);
        } else if (_submittedSearchQuery != null) {
            setTitle(getString(R.string.search));
            getSupportActionBar().setSubtitle(_submittedSearchQuery);
            _entryListView.setSearchFilter(_submittedSearchQuery);
            _searchViewBackPressHandler.setEnabled(true);
        } else if (_prefs.getFocusSearchEnabled() && !_isRecreated) {
            _searchView.setIconified(false);
            _searchView.setFocusable(true);
            _searchView.requestFocus();
            _searchView.requestFocusFromTouch();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_settings) {
            startPreferencesActivity();
        } else if (itemId == R.id.action_about) {
            Intent intent = new Intent(this, AboutActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.action_lock) {
            _vaultManager.lock(true);
        } else {
            if (item.getGroupId() == R.id.action_sort_category) {
                item.setChecked(true);

                SortCategory sortCategory;
                int subItemId = item.getItemId();
                if (subItemId == R.id.menu_sort_alphabetically) {
                    sortCategory = SortCategory.ISSUER;
                } else if (subItemId == R.id.menu_sort_alphabetically_reverse) {
                    sortCategory = SortCategory.ISSUER_REVERSED;
                } else if (subItemId == R.id.menu_sort_alphabetically_name) {
                    sortCategory = SortCategory.ACCOUNT;
                } else if (subItemId == R.id.menu_sort_alphabetically_name_reverse) {
                    sortCategory = SortCategory.ACCOUNT_REVERSED;
                } else if (subItemId == R.id.menu_sort_usage_count) {
                    sortCategory = SortCategory.USAGE_COUNT;
                } else {
                    sortCategory = SortCategory.CUSTOM;
                }

                _entryListView.setSortCategory(sortCategory, true);
                _prefs.setCurrentSortCategory(sortCategory);
            }

            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void collapseSearchView() {
        _searchView.setQuery(null, false);
        _searchView.setIconified(true);
    }

    private void loadEntries() {
        if (!_loaded) {
            _entryListView.setUsageCounts(_prefs.getUsageCounts());
            _entryListView.addEntries(_vaultManager.getVault().getEntries());
            _entryListView.runEntriesAnimation();
            _loaded = true;
        }
    }

    private void startAuthActivity(boolean inhibitBioPrompt) {
        if (!_isAuthenticating) {
            Intent intent = new Intent(this, AuthActivity.class);
            intent.putExtra("inhibitBioPrompt", inhibitBioPrompt);
            authResultLauncher.launch(intent);
            _isAuthenticating = true;
        }
    }

    private void updateLockIcon() {
        // hide the lock icon if the vault is not unlocked
        if (_menu != null && _vaultManager.isVaultLoaded()) {
            MenuItem item = _menu.findItem(R.id.action_lock);
            item.setVisible(_vaultManager.getVault().isEncryptionEnabled());
        }
    }

   private void updateErrorCard() {
       ErrorCardInfo info = null;

       Preferences.BackupResult backupRes = _prefs.getErroredBackupResult();
       if (backupRes != null) {
           info = new ErrorCardInfo(getString(R.string.backup_error_bar_message), view -> {
               Dialogs.showBackupErrorDialog(this, backupRes, (dialog, which) -> {
                   startPreferencesActivity(BackupsPreferencesFragment.class, "pref_backups");
               });
           });
       } else if (_prefs.isBackupsReminderNeeded() && _prefs.isBackupReminderEnabled()) {
           String text;
           Date date = _prefs.getLatestBackupOrExportTime();
           if (date != null) {
               text = getString(R.string.backup_reminder_bar_message_with_latest, TimeUtils.getElapsedSince(this, date));
           } else {
               text = getString(R.string.backup_reminder_bar_message);
           }
           info = new ErrorCardInfo(text, view -> {
               Dialogs.showSecureDialog(new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Aegis_AlertDialog_Error)
                       .setTitle(R.string.backup_reminder_bar_dialog_title)
                       .setMessage(R.string.backup_reminder_bar_dialog_summary)
                       .setIconAttribute(android.R.attr.alertDialogIcon)
                       .setPositiveButton(R.string.backup_reminder_bar_dialog_accept, (dialog, whichButton) -> {
                           startPreferencesActivity(BackupsPreferencesFragment.class, "pref_backups");
                       })
                       .setNegativeButton(android.R.string.cancel, null)
                       .create());
           });
       } else if (_prefs.isPlaintextBackupWarningNeeded()) {
           info = new ErrorCardInfo(getString(R.string.backup_plaintext_export_warning), view -> showPlaintextExportWarningOptions());
       }

       _entryListView.setErrorCardInfo(info);
   }

    private void showPlaintextExportWarningOptions() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_plaintext_warning, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Aegis_AlertDialog_Warning)
                .setTitle(R.string.backup_plaintext_export_warning)
                .setView(view)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        CheckBox checkBox = view.findViewById(R.id.checkbox_plaintext_warning);
        checkBox.setChecked(false);

        dialog.setOnShowListener(d -> {
            Button btnPos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);

            btnPos.setOnClickListener(l -> {
                dialog.dismiss();

                _prefs.setIsPlaintextBackupWarningDisabled(checkBox.isChecked());
                _prefs.setIsPlaintextBackupWarningNeeded(false);

                updateErrorCard();
            });
        });

        Dialogs.showSecureDialog(dialog);
    }

    @Override
    public void onEntryClick(VaultEntry entry) {
        if (_actionMode != null) {
            if (_selectedEntries.isEmpty()) {
                _actionMode.finish();
            } else {
                setFavoriteMenuItemVisiblity();
                setIsMultipleSelected(_selectedEntries.size() > 1);
            }
        }
    }

    @Override
    public void onSelect(VaultEntry entry) {
        _selectedEntries.add(entry);
    }

    @Override
    public void onDeselect(VaultEntry entry) {
        _selectedEntries.remove(entry);
    }

    private void setIsMultipleSelected(boolean multipleSelected) {
        _entryListView.setIsLongPressDragEnabled(!multipleSelected);
        _actionMode.getMenu().findItem(R.id.action_edit).setVisible(!multipleSelected);
        _actionMode.getMenu().findItem(R.id.action_copy).setVisible(!multipleSelected);
    }

    private void setAssignIconsMenuItemVisibility() {
        MenuItem assignIconsMenuItem = _actionMode.getMenu().findItem(R.id.action_assign_icons);
        assignIconsMenuItem.setVisible(_iconPackManager.hasIconPack());
    }

    private void setFavoriteMenuItemVisiblity() {
        MenuItem toggleFavoriteMenuItem = _actionMode.getMenu().findItem(R.id.action_toggle_favorite);

        if (_selectedEntries.size() == 1){
            if (_selectedEntries.get(0).isFavorite()) {
                toggleFavoriteMenuItem.setIcon(R.drawable.ic_filled_star_24);
                toggleFavoriteMenuItem.setTitle(R.string.unfavorite);
            } else {
                toggleFavoriteMenuItem.setIcon(R.drawable.ic_outline_star_24);
                toggleFavoriteMenuItem.setTitle(R.string.favorite);
            }
        } else {
            toggleFavoriteMenuItem.setIcon(R.drawable.ic_outline_star_24);
            toggleFavoriteMenuItem.setTitle(String.format("%s / %s", getString(R.string.favorite), getString(R.string.unfavorite)));
        }
    }

    @Override
    public void onLongEntryClick(VaultEntry entry) {
        if (!_selectedEntries.isEmpty()) {
            return;
        }

        _selectedEntries.add(entry);
        _entryListView.setActionModeState(true, entry);
        startActionMode();
    }

    private void startActionMode() {
        _actionMode = startSupportActionMode(_actionModeCallbacks);
        _actionModeBackPressHandler.setEnabled(true);
        setFavoriteMenuItemVisiblity();
        setAssignIconsMenuItemVisibility();
    }

    @Override
    public void onEntryMove(VaultEntry entry1, VaultEntry entry2) {
        _vaultManager.getVault().moveEntry(entry1, entry2);
    }

    @Override
    public void onEntryDrop(VaultEntry entry) {
        saveVault();
    }

    @Override
    public void onEntryChange(VaultEntry entry) {
        saveAndBackupVault();
    }

    public void onEntryCopy(VaultEntry entry) {
        copyEntryCode(entry);
    }

    @Override
    public void onScroll(int dx, int dy) {
        if (!_isDPadPressed) {
            _fabScrollHelper.onScroll(dx, dy);
        }
    }

    @Override
    public void onListChange() { _fabScrollHelper.setVisible(true); }

    @Override
    public void onSaveGroupFilter(Set<UUID> groupFilter) {
        _prefs.setGroupFilter(groupFilter);
    }

    @Override
    public void onLocked(boolean userInitiated) {
        if (_actionMode != null) {
            _actionMode.finish();
        }
        if (_searchView != null && !_searchView.isIconified()) {
            collapseSearchView();
        }

        _entryListView.clearEntries();
        _loaded = false;

        if (userInitiated) {
            startAuthActivity(true);
        } else {
            super.onLocked(false);
        }
    }

    @SuppressLint("InlinedApi")
    private void copyEntryCode(VaultEntry entry) {
        String otp;
        try {
            otp = entry.getInfo().getOtp();
        } catch (OtpInfoException e) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text/plain", otp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            clip.getDescription().setExtras(extras);
        }
        clipboard.setPrimaryClip(clip);
        if (_prefs.isMinimizeOnCopyEnabled()) {
            moveTaskToBack(true);
        }
    }

    private class SearchViewBackPressHandler extends OnBackPressedCallback {
        public SearchViewBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            if (!_searchView.isIconified() || _submittedSearchQuery != null) {
                _submittedSearchQuery = null;
                _pendingSearchQuery = null;
                _entryListView.setSearchFilter(null);

                collapseSearchView();
                setTitle(R.string.app_name);
                getSupportActionBar().setSubtitle(null);
            }
        }
    }

    private class LockBackPressHandler extends OnBackPressedCallback {
        public LockBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            if (_vaultManager.isAutoLockEnabled(Preferences.AUTO_LOCK_ON_BACK_BUTTON)) {
                _vaultManager.lock(false);
            }
        }
    }

    private class ActionModeBackPressHandler extends OnBackPressedCallback {
        public ActionModeBackPressHandler() {
            super(false);
        }

        @Override
        public void handleOnBackPressed() {
            if (_actionMode != null) {
                _actionMode.finish();
            }
        }
    }

    private class ActionModeCallbacks implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_action_mode, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (_selectedEntries.size() == 0) {
                mode.finish();
                return true;
            }

            int itemId = item.getItemId();
            if (itemId == R.id.action_copy) {
                copyEntryCode(_selectedEntries.get(0));
                mode.finish();
            } else if (itemId == R.id.action_edit) {
                startEditEntryActivity(_selectedEntries.get(0));
                mode.finish();
            } else if (itemId == R.id.action_toggle_favorite) {
                for (VaultEntry entry : _selectedEntries) {
                    entry.setIsFavorite(!entry.isFavorite());
                    _entryListView.replaceEntry(entry.getUUID(), entry);
                }
                _entryListView.refresh(true);

                saveAndBackupVault();
                mode.finish();
            } else if (itemId == R.id.action_share_qr) {
                Intent intent = new Intent(getBaseContext(), TransferEntriesActivity.class);
                ArrayList<GoogleAuthInfo> authInfos = new ArrayList<>();
                for (VaultEntry entry : _selectedEntries) {
                    GoogleAuthInfo authInfo = new GoogleAuthInfo(entry.getInfo(), entry.getName(), entry.getIssuer());
                    authInfos.add(authInfo);
                }

                intent.putExtra("authInfos", authInfos);
                startActivity(intent);

                mode.finish();
            } else if (itemId == R.id.action_delete) {
                Dialogs.showDeleteEntriesDialog(MainActivity.this, _selectedEntries, (d, which) -> {
                    deleteEntries(_selectedEntries);
                    _entryListView.setGroups(_vaultManager.getVault().getUsedGroups());
                    mode.finish();
                });
            } else if (itemId == R.id.action_select_all) {
                _selectedEntries = _entryListView.selectAllEntries();
                setFavoriteMenuItemVisiblity();
                setIsMultipleSelected(_selectedEntries.size() > 1);
            } else if (itemId == R.id.action_assign_icons) {
                startAssignIconsActivity(_selectedEntries);
                mode.finish();
            } else {
                return false;
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            _entryListView.setActionModeState(false, null);
            _actionModeBackPressHandler.setEnabled(false);
            _selectedEntries.clear();
            _actionMode = null;
        }
    }
}
