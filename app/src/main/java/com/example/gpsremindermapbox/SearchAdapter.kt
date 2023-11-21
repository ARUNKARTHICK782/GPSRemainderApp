package com.example.gpsremindermapbox

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gpsremindermapbox.listener.SelectedSearchChangeEvent
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import java.text.DecimalFormat

class SearchAdapter(
    private var searches: MutableList<PlaceAutocompleteSuggestion>,
    private var ref: Context,
    private var listener: SelectedSearchChangeEvent
) : RecyclerView.Adapter<SearchAdapter.SearchViewHolder>() {

    class SearchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val parent: LinearLayout = itemView.findViewById(R.id.searchParent)
        val title: TextView = itemView.findViewById(R.id.searchTitle)
        val description: TextView = itemView.findViewById(R.id.searchDescription)
        val distance: TextView = itemView.findViewById(R.id.searchDistance)
        val divider: View = itemView.findViewById(R.id.searchDivider)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.search_item, parent, false)
        return SearchViewHolder(view)
    }

    override fun getItemCount(): Int {
        return searches.size
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        holder.title.text = searches[position].name
        holder.description.text = searches[position].formattedAddress
        holder.distance.text = ref.getString(
            R.string.searchKm,
            DecimalFormat("######.#").format(searches[position].distanceMeters!!.div(1000))
        )

        if (position == searches.size - 1) holder.divider.visibility = View.GONE

        holder.parent.setOnClickListener {
            Log.i("SearchAdapter Says", searches[position].distanceMeters.toString())
            listener.onSelectedSearchChange(searches[position])
        }
    }
}