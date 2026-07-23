package com.bettingpicker.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bettingpicker.app.R
import com.bettingpicker.app.model.Pick
import com.bettingpicker.app.model.PickKind
import com.bettingpicker.app.model.Tier

class PicksAdapter : RecyclerView.Adapter<PicksAdapter.VH>() {

    private val items = ArrayList<Pick>()

    fun submit(newItems: List<Pick>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun snapshot(): List<Pick> = items.toList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val sport: TextView = view.findViewById(R.id.txtSport)
        val tier: TextView = view.findViewById(R.id.txtTier)
        val matchup: TextView = view.findViewById(R.id.txtMatchup)
        val kind: TextView = view.findViewById(R.id.txtKind)
        val winrate: TextView = view.findViewById(R.id.txtWinrate)
        val sample: TextView = view.findViewById(R.id.txtSample)
        val comp: TextView = view.findViewById(R.id.txtCompetition)
        val h2h: TextView = view.findViewById(R.id.txtH2H)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pick, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.sport.text = "${p.sport.emoji} ${p.sport.displayName}"
        holder.tier.text = "${p.tier.display} confidence"
        holder.tier.setTextColor(colorForTier(p.tier))
        holder.matchup.text = "${p.pickedTeamName} vs ${p.opponentName}"
        val slotLabel = if (p.pickKind == PickKind.HOME) p.sport.primaryLabel else p.sport.secondaryLabel
        holder.kind.text = "Pick: $slotLabel (last ${p.sampleSize} matches)"
        holder.winrate.text = "Weighted score: %.1f%%  (raw win-rate: %.1f%%)".format(
            p.weightedWinRate, p.winRateLast20
        )
        holder.sample.text = "Kickoff: ${p.kickoffUtc}"
        holder.comp.text = p.competition

        if (p.h2hMatches == 0) {
            holder.h2h.text = "H2H: no data"
            holder.h2h.setTextColor(Color.parseColor("#999999"))
            holder.h2h.setTypeface(null, Typeface.NORMAL)
        } else {
            val tag = if (p.h2hFavourable) "STRONG" else "—"
            holder.h2h.text = "H2H weighted: %.1f%% (raw %.1f%%) in %d meetings  %s".format(
                p.h2hWeightedWinRate, p.h2hWinRate, p.h2hMatches, tag
            )
            holder.h2h.setTextColor(
                if (p.h2hFavourable) Color.parseColor("#2E7D32")
                else Color.parseColor("#555555")
            )
            holder.h2h.setTypeface(
                if (p.h2hFavourable) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }

    private fun colorForTier(tier: Tier): Int = when (tier) {
        Tier.HIGH   -> Color.parseColor("#2E7D32")
        Tier.MEDIUM -> Color.parseColor("#F9A825")
        Tier.LOW    -> Color.parseColor("#C62828")
    }
}
