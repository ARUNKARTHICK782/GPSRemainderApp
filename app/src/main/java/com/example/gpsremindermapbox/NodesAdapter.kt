package com.example.gpsremindermapbox

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.gpsremindermapbox.listener.StartingPointChangeEvent

class NodesAdapter constructor(
    private var nodes: MutableList<NodePoint>,
    private var ref: Context,
    private var listener: StartingPointChangeEvent
) :
    RecyclerView.Adapter<NodesAdapter.NodesViewHolder>() {

    var curIndex = -1

    class NodesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val number: TextView = itemView.findViewById(R.id.nodeNumber)
        val parent: RelativeLayout = itemView.findViewById(R.id.nodeParent)
        val priority: TextView = itemView.findViewById(R.id.nodePriority)
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodesViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.node_item, parent, false)
        return NodesViewHolder(view)
    }

    override fun getItemCount(): Int {
        return nodes.size
    }

    fun notifyAdded() {
        notifyItemInserted(nodes.size - 1)
    }

    fun notifyAllChange() {
        notifyItemRangeChanged(0, nodes.size)
    }

    fun clear(size: Int) {
        curIndex = -1
        notifyItemRangeRemoved(0, size)
    }

    override fun onBindViewHolder(holder: NodesViewHolder, position: Int) {
        holder.number.text = nodes[position].pos.toString()
        holder.priority.text = nodes[position].priority.toString()

        when (nodes[position].priority) {
            1 -> holder.priority.setTextColor(ref.getColor(R.color.prior_1))
            2 -> holder.priority.setTextColor(ref.getColor(R.color.prior_2))
            3 -> holder.priority.setTextColor(ref.getColor(R.color.prior_3))
            4 -> holder.priority.setTextColor(ref.getColor(R.color.prior_4))
            5 -> holder.priority.setTextColor(ref.getColor(R.color.prior_5))
        }

        if (curIndex == nodes[position].pos) {
            holder.parent.background = ContextCompat.getDrawable(ref, R.drawable.node_selected_bg)
            holder.number.setTextColor(ContextCompat.getColor(ref, R.color.white))
        } else {
            holder.parent.background = ContextCompat.getDrawable(ref, R.drawable.node_bg)
            holder.number.setTextColor(ContextCompat.getColor(ref, R.color.black))
        }

        holder.parent.setOnClickListener {
            if (!Algorithm.isDirecting) {
                notifyItemChanged(curIndex)
                listener.onStartPointChange(curIndex, position)
                curIndex = position
                notifyItemChanged(curIndex)
            }
        }
    }
}