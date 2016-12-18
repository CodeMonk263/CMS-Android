package crux.bphc.cms;

import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.Constants;
import helper.ClickListener;
import helper.DividerItemDecoration;
import io.realm.Realm;
import io.realm.RealmResults;
import set.Content;
import set.CourseSection;
import set.Module;

public class CourseModulesActivity extends AppCompatActivity {
    Realm realm;
    List<Module> modules;
    ArrayList<String> requestedDownloads;
    List<String> fileList;
    MyAdapter myAdapter;
    BroadcastReceiver onComplete = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            int i = 0;
            for (String fileName : requestedDownloads) {
                if (searchFile(fileName, i == 0)) {
                    requestedDownloads.remove(fileName);
                    setDownloaded(fileName);
                    return;
                }
                i++;
            }
        }
    };

    private void setDownloaded(String fileName) {
        myAdapter.notifyDataSetChanged();
    }

    private boolean searchFile(String fileName, boolean reload) {
        if (reload || fileList == null) {
            File direc = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            fileList = Arrays.asList(direc.list());
            if (fileList.size() == 0) {
                return false;
            }
        }
        if (fileList.contains(fileName)) {
            Log.d("File found:", fileName);
            return true;
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int sectionID = getIntent().getIntExtra("id", -1);
        if (sectionID == -1) {
            finish();
            return;
        }
        setContentView(R.layout.activity_course_modules);

        requestedDownloads = new ArrayList<>();
        realm = Realm.getDefaultInstance();
        RealmResults<CourseSection> sections = realm.where(CourseSection.class).equalTo("id", sectionID).findAll();


        modules = realm.copyFromRealm(sections.first().getModules());

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        myAdapter = new MyAdapter(this);
        myAdapter.setModules(modules);
        recyclerView.setAdapter(myAdapter);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.addItemDecoration(dividerItemDecoration);
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        myAdapter.setClickListener(new ClickListener() {
            @Override
            public boolean onClick(Object object, int position) {
                if (object instanceof Module) {
                    Module module = (Module) object;
                    if (module.getContents() == null || module.getContents().size() == 0) {
                        //todo open label/forum/etc in new activity
                        return true;
                    }

                    for (Content content : module.getContents()) {
                        if (!searchFile(content.getFilename(), false)) {
                            requestedDownloads.add(content.getFilename());
                            downloadFile(content, module);
                        } else {
                            openFile(content);
                        }
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void openFile(Content content) {
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath()
                + File.separator +
                content.getFilename());
        Uri path = Uri.fromFile(file);
        Intent pdfOpenintent = new Intent(Intent.ACTION_VIEW);
        pdfOpenintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        pdfOpenintent.setDataAndType(path, "application/" + getExtension(content.getFilename()));
        try {
            CourseModulesActivity.this.startActivity(pdfOpenintent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(CourseModulesActivity.this, "No app found to open the file.", Toast.LENGTH_SHORT).show();
            pdfOpenintent.setDataAndType(path, "application/*");
            CourseModulesActivity.this.startActivity(pdfOpenintent);
        }
    }

    @NonNull
    private String getExtension(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private void downloadFile(Content content, Module module) {
        String url = content.getFileurl() + "&token=" + Constants.TOKEN;
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription(module.getModname());
        request.setTitle(content.getFilename());

        request.allowScanningByMediaScanner();
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, content.getFilename());
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

        manager.enqueue(request);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private class MyAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        Context context;
        LayoutInflater inflater;

        List<Module> modules;
        ClickListener clickListener;

        MyAdapter(Context context) {
            this.context = context;
            inflater = LayoutInflater.from(context);
            modules = new ArrayList<>();

        }

        @Override
        public int getItemViewType(int position) {
            return modules.get(position).getModType();
        }

        void setModules(List<Module> modules) {
            this.modules = modules;
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

            return new ViewHolderResource(inflater.inflate(R.layout.row_course_module_resource, parent, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {

            ((ViewHolderResource) holder).bind(modules.get(position));
        }

        @Override
        public int getItemCount() {
            return modules.size();
        }

        public void setClickListener(ClickListener clickListener) {
            this.clickListener = clickListener;
        }


        class ViewHolderResource extends RecyclerView.ViewHolder {
            TextView name;
            ImageView modIcon, download;
            int downloaded = -1;

            ViewHolderResource(View itemView) {
                super(itemView);

                name = (TextView) itemView.findViewById(R.id.fileName);
                modIcon = (ImageView) itemView.findViewById(R.id.fileIcon);
                download = (ImageView) itemView.findViewById(R.id.download);
                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (clickListener != null) {
                            clickListener.onClick(modules.get(getLayoutPosition()), getLayoutPosition());
                        }
                    }
                });
            }

            void bind(Module module) {
                name.setText(module.getName());
                modIcon.setVisibility(View.VISIBLE);
                if (module.getContents() == null || module.getContents().size() == 0) {
                    download.setVisibility(View.GONE);
                } else {
                    download.setVisibility(View.VISIBLE);
                    if (downloaded == -1 || downloaded == 0) {
                        List<Content> contents = module.getContents();
                        downloaded = 1;
                        for (Content content : contents) {
                            if (!searchFile(content.getFilename(), false)) {
                                downloaded = 0;
                                break;
                            }
                        }
                    }

                    if (downloaded == 1) {
                        download.setImageResource(R.drawable.open_in_app);
                    } else {
                        download.setImageResource(R.drawable.content_save);
                    }
                }

                switch (module.getModType()) {
                    case 0:
                    case 100:
                        Picasso.with(context).load(module.getModicon()).into(modIcon);
                        break;
                    case 3:
                        modIcon.setImageResource(R.drawable.book);
                        break;
                    case 4:
                        modIcon.setImageResource(R.drawable.folder);
                        break;
                    case 2:
                        modIcon.setVisibility(View.GONE);
                        break;
                    case 1:
                        modIcon.setImageResource(R.drawable.forum);
                        break;

                }
            }
        }

    }
}