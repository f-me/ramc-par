package f.me.ramc;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ListActivity;
import android.content.Intent;

public class InfoActivity extends ListActivity {

	static final String[] TITLES = new String[] {
		"Стандарты общения с клиентом",
		"Правила безопасности при проведении работ",
		"Инструкция водителя эвакуатора"
	};
	
	static final String[] HTML_FILES = new String[] {
		"customer.html",
		"safety.html",
		"evacuation.html"
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, TITLES));
		
		ListView listView = getListView();
		listView.setTextFilterEnabled(true);
		listView.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position,
					long id) {
				
				Intent intent = new Intent(InfoActivity.this, WebViewActivity.class);
			    intent.putExtra(WebViewActivity.HTML_FILE_NAME, HTML_FILES[(int) id]);
				startActivity(intent);
			}
			
		});
		
	}

}
