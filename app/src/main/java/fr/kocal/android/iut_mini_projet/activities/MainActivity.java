package fr.kocal.android.iut_mini_projet.activities;

import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import fr.kocal.android.iut_mini_projet.AlertLevel;
import fr.kocal.android.iut_mini_projet.Earthquake;
import fr.kocal.android.iut_mini_projet.R;
import fr.kocal.android.iut_mini_projet.adapters.EarthquakeAdapter;
import fr.kocal.android.iut_mini_projet.asyncTasks.AsyncDownloader;
import fr.kocal.android.iut_mini_projet.contracts.EarthquakeContract.EarthquakeEntry;
import fr.kocal.android.iut_mini_projet.eventListeners.OnContentDownloaded;
import fr.kocal.android.iut_mini_projet.helpers.EarthquakeDbHelper;

public class MainActivity extends AppCompatActivity {

    /**
     * Base de données de l'application
     */
    SQLiteDatabase dbReadable, dbWritable;

    /**
     * JSON qui contient les derniers tremblements de terre
     */
    JSONObject json;

    /**
     * ArrayList qui contient les tremblements de terre
     */
    ArrayList<Earthquake> earthquakes;

    /**
     * Fait le lien entre la valeur des actions "^action_display_earthquake_past.*"
     */
    HashMap<Integer, String> earthquakesUrls;

    /**
     * Liste les tremblements de terre
     */
    ListView mListView;

    /**
     * Adapter associé à la listView mListView;
     */
    EarthquakeAdapter earthquakeAdapter;

    /**
     * Loader qui s'affichera lors du chargement des tremblements
     */
    ProgressBar mProgressBar;

    /**
     * Message qui s'affichera lorsqu'il n'y aura aucun tremblements à afficher
     */
    TextView mNoEarthquakes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initSomeUiElements();
        initDatabase();
        initToolbar();

        fetchJson();
        updateToolbarTitle();
        initListView();

        initEarthquakesUrls();
    }

    /**
     * Initialise certains éléments graphiques
     */
    private void initSomeUiElements() {
        mProgressBar = (ProgressBar) findViewById(R.id.loaderMain);
        mNoEarthquakes = (TextView) findViewById(R.id.noEarthquakes);
    }

    /**
     * Initialise la base de données
     */
    private void initDatabase() {
        EarthquakeDbHelper mDbHelper = new EarthquakeDbHelper(getApplicationContext(), EarthquakeDbHelper.DATABASE_NAME, null, EarthquakeDbHelper.DATABASE_VERSION);
        dbReadable = mDbHelper.getReadableDatabase();
        dbWritable = mDbHelper.getWritableDatabase();
    }

    /**
     * Initialise la toolbar
     */
    private void initToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    /**
     * Met à jour le titre de la toolbar.
     * Soit on affiche le titre du JSON, soit on affiche le titre par défaut
     */
    private void updateToolbarTitle() {
        try {
            // Soit on affiche le titre du JSON
            getSupportActionBar().setTitle(json.getJSONObject("metadata").getString("title"));
        } catch (JSONException e) {
            // Soit le titre par défaut s'il y a eu une erreur
            getSupportActionBar().setTitle(getString(R.string.titleDisplayingEarthquakes));
            e.printStackTrace();
        }
    }

    /**
     * Récupère le JSON envoyé par le SplashScreen et l'assigne dans l'attribut MainActivity::json
     *
     * @return boolean
     */
    private boolean fetchJson() {
        try {
            json = new JSONObject(getIntent().getStringExtra("JSON"));
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    /**
     * Initialise la listview des tremblements de terre
     */
    private void initListView() {
        mListView = (ListView) findViewById(R.id.listView);
        earthquakes = extractEarthquakesFromJson();
        earthquakeAdapter = new EarthquakeAdapter(MainActivity.this, earthquakes, dbReadable);

        mListView.setAdapter(earthquakeAdapter);
        mListView.setFastScrollEnabled(true); // GOTTA GO FAST
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Earthquake earthquake = (Earthquake) mListView.getItemAtPosition(position);
                Intent i = new Intent(MainActivity.this, EarthquakeActivity.class);
                i.putExtra("earthquake", earthquake);
                startActivity(i);
            }
        });
    }

    /**
     * Extrait les tremblements de terre sous forme d'ArrayList du JSON
     *
     * @return ArrayList<Earthquake>
     */
    private ArrayList<Earthquake> extractEarthquakesFromJson() {
        ArrayList<Earthquake> earthquakes = new ArrayList<>();

        try {
            JSONArray features = json.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                Earthquake earthquake = new Earthquake();
                int isFavorite = 0;

                JSONObject feature = features.getJSONObject(i);
                JSONObject properties = feature.getJSONObject("properties");
                JSONObject geometry = feature.getJSONObject("geometry");

                // On récupère les coordonnées
                JSONArray jsonArrayCoordinates = geometry.getJSONArray("coordinates");
                Double[] coordinates = new Double[]{
                        jsonArrayCoordinates.getDouble(0),
                        jsonArrayCoordinates.getDouble(1),
                        jsonArrayCoordinates.getDouble(2)
                };

                // On récupère le level
                String alertString = properties.getString("alert");
                AlertLevel alert = AlertLevel.getColor(alertString);

                // On récupère le fav ou non dans la bdd
                Cursor c = dbReadable.query(EarthquakeEntry.TABLE_NAME,
                        new String[]{EarthquakeEntry.COLUMN_NAME_ID, EarthquakeEntry.COLUMN_NAME_FAVORITE},
                        EarthquakeEntry.COLUMN_NAME_ID + " = ?",
                        new String[]{feature.getString("id")},
                        null, null, null);

                // On a un truc dans la BDD
                if (c != null && c.moveToFirst()) {
                    isFavorite = c.getInt(c.getColumnIndexOrThrow(EarthquakeEntry.COLUMN_NAME_FAVORITE));
                    c.close();
                } else {
                    // On a rien dans la BDD
                    ContentValues values = new ContentValues();
                    values.put(EarthquakeEntry.COLUMN_NAME_ID, feature.getString("id"));
                    values.put(EarthquakeEntry.COLUMN_NAME_FAVORITE, isFavorite);
                    dbWritable.insert(EarthquakeEntry.TABLE_NAME, null, values);
                }

                // On fill !
                earthquake.setId(feature.getString("id"));
                earthquake.setPlace(properties.getString("place"));
                earthquake.setMagnitude(properties.getDouble("mag"));
                earthquake.setTime(properties.getLong("time"));
                earthquake.setCoordinates(coordinates);
                earthquake.setDetailsUrl(properties.getString("detail"));
                earthquake.setUrl(properties.getString("url"));
                earthquake.setAlertLevel(alert);
                earthquake.setInFavorite((isFavorite != 0));

                earthquakes.add(earthquake);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return earthquakes;
    }

    /**
     * Affiche tous les tremblements sur une maps
     */
    private void displayOnMap() {
        Intent i = new Intent(MainActivity.this, ShowOnMaps.class);
        i.putExtra("earthquakes", earthquakes);
        startActivity(i);
    }

    /**
     * Récupère le JSON de l'url passée en paramètre + l'affiche dans la listView
     *
     * @param url
     */
    private void fetchJsonAndDisplay(String url) {
        // On prépare les animations
        mNoEarthquakes.setAlpha(0f);
        mProgressBar.animate().alpha(1f);
        mListView.animate().alpha(0f).translationY(-mListView.getHeight()).withEndAction(new Runnable() {
            @Override
            public void run() {
                mListView.setTranslationY(100);
            }
        });

        // On télécharge le JSON o/
        new AsyncDownloader<JSONObject>(JSONObject.class, new OnContentDownloaded<JSONObject>() {
            @Override
            public void onDownloaded(Error error, JSONObject jsonObject) {
                // On a eu une erreur
                if (error != null) {
                    Toast.makeText(MainActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }

                json = jsonObject;

                // Ivre, il pensait que lancer son `earthquakeAdapter.setNewEarthquakes` dans un nouveau Thread
                // allait régler ses problèmes de freeze de l'UI lorsqu'on charge BEAUCOUP de tremblemens,
                // mais sans succès :-)
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        earthquakes = extractEarthquakesFromJson();
//                        earthquakeAdapter.setNewEarthquakes(earthquakes);
                        initListView();
                        updateToolbarTitle();

                        mProgressBar.animate().alpha(0f);

                        // Pas de tremblements
                        if (earthquakes.size() == 0) {
                            mNoEarthquakes.animate().alpha(1f);
                        } else {
                            mListView.animate().alpha(1f).translationY(0);
                        }
                    }
                }).run();

            }
        }).execute(url);
    }

    /**
     * Affiche soit les favoris soit tous les tremblements
     *
     * @param item
     */
    private void toggleFavorites(MenuItem item) {
        if (item.isChecked()) {
            item.setChecked(false);
            earthquakeAdapter.getFavoriteFilter().filter(earthquakeAdapter.DISPLAY_ALL);
        } else {
            item.setChecked(true);
            earthquakeAdapter.getFavoriteFilter().filter(earthquakeAdapter.DISPLAY_ONLY_FAVORITE);
        }
    }

    /**
     * Hashmap bo je
     */
    private void initEarthquakesUrls() {
        earthquakesUrls = new HashMap<>();
        earthquakesUrls.put(R.id.action_display_earthquake_past_hour_significiant, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_hour.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_hour_magnitude_4_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_hour.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_hour_magnitude_2_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_hour.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_hour_magnitude_1, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/1.0_hour.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_hour_list_all, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson");

        earthquakesUrls.put(R.id.action_display_earthquake_past_day_significiant, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_day.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_day_magnitude_4_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_day.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_day_magnitude_2_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_day_magnitude_1, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/1.0_day.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_day_list_all, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson");

        earthquakesUrls.put(R.id.action_display_earthquake_past_week_significiant, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_week.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_week_magnitude_4_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_week.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_week_magnitude_2_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_week_magnitude_1, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/1.0_week.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_week_list_all, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_week.geojson");

        earthquakesUrls.put(R.id.action_display_earthquake_past_month_significiant, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/significant_month.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_month_magnitude_4_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/4.5_month.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_month_magnitude_2_5, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_month.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_month_magnitude_1, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/1.0_month.geojson");
        earthquakesUrls.put(R.id.action_display_earthquake_past_month_list_all, "http://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_month.geojson");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView search = (SearchView) menu.findItem(R.id.action_search).getActionView();

        // Gestion de la recherche dans la listView
        search.setSearchableInfo(manager.getSearchableInfo(getComponentName()));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                Log.v("Kocal", "Search : " + query);
                earthquakeAdapter.getFilter().filter(query);
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (earthquakesUrls.containsKey(id)) {
            fetchJsonAndDisplay(earthquakesUrls.get(id));
            return true;
        }

        switch (id) {
            case R.id.action_display_on_map:
                displayOnMap();
                return true;

            case R.id.action_display_only_favorites:
                toggleFavorites(item);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
