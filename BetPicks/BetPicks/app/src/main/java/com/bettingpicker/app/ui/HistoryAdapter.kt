package com.bettingpicker.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bettingpicker.app.R
import com.bettingpicker.app.model.PickHistoryEntry
import com.bettingpicker.app.model.PickKind
import com.bettingpicker.app.model.PickOutcome

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = ArrayList<PickHistoryEntry>()

    fun submit(newItems: List<PickHistoryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun current(): List<PickHistoryEntry> = items.toList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val sport: TextView = view.findViewById(R.id.txtHistorySport)
        val result: TextView = view.findViewById(R.id.txtResult)
        val matchup: TextView = view.findViewById(R.id.txtHistoryMatchup)
        val kind: TextView = view.findViewById(R.id.txtHistoryKind)
        val winrate: TextView = view.findViewById(R.id.txtHistoryWinrate)
        val score: TextView = view.findViewById(R.id.txtHistoryScore)
        val issued: TextView = view.findViewById(R.id.txtHistoryIssued)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.sport.text = "${e.sport.emoji} ${e.sport.displayName}"
        holder.matchup.text = "${e.pickedTeamName} vs ${e.opponentName}"
        val slotLabel = if (e.pickKind == PickKind.HOME) e.sport.primaryLabel else e.sport.secondaryLabel
        holder.kind.text = "Pick: $slotLabel • ${e.tier.display} • ${e.competition}"
        holder.winrate.text = "Weighted: %.1f%% (raw %.1f%%)".format(e.weightedWinRateAtIssue, e.winRateAtIssue) +
            if (e.h2hFavourable) "  •  H2H %.1f%% weighted (strong)".format(e.h2hWeightedWinRate)
            else "  •  H2H %.1f%% weighted".format(e.h2hWeightedWinRate)
        holder.score.text = e.finalScore?.let { "Final score: $it" } ?: "Awaiting result"
        holder.issued.text = "Issued: ${e.issuedAtUtc} • Kickoff: ${e.kickoffUtc}"

        when (e.status) {
            PickOutcome.WON -> {
                holder.result.text = "WON"
                holder.result.setTextColor(Color.parseColor("#2E7D32"))
                holder.result.setTypeface(null, Typeface.BOLD)
            }
            PickOutcome.LOST -> {
                holder.result.text = "LOST"
                holder.result.setTextColor(Color.parseColor("#C62828"))
                holder.result.setTypeface(null, Typeface.BOLD)
            }
            PickOutcome.PENDING -> {
                holder.result.text = "PENDING"
                holder.result.setTextColor(Color.parseColor("#F9A825"))
                holder.result.setTypeface(null, Typeface.NORMAL)
            }
        }
    }
}
