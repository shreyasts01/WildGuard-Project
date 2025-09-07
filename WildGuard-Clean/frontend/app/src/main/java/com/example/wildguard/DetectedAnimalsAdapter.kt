package com.example.wildguard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class DetectedAnimalsAdapter : RecyclerView.Adapter<DetectedAnimalsAdapter.AnimalViewHolder>() {

    private var items: List<DetectedAnimal> = emptyList()
    private val sdf = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

    fun setData(newItems: List<DetectedAnimal>) {
        items = newItems
        notifyDataSetChanged()
    }

    class AnimalViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val labelText: TextView = view.findViewById(R.id.textAnimalLabel)
        val distanceText: TextView = view.findViewById(R.id.textAnimalDistance)
        val timeText: TextView = view.findViewById(R.id.textAnimalTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detected_animal, parent, false)
        return AnimalViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AnimalViewHolder, position: Int) {
        val item = items[position]
        holder.labelText.text = item.label
        holder.distanceText.text =
            if (item.distanceMeters >= 1000)
                String.format("%.2f km", item.distanceMeters / 1000)
            else
                String.format("%.0f m", item.distanceMeters)
        holder.timeText.text = sdf.format(item.timestamp)
    }
}
