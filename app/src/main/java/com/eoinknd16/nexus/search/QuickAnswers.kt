package com.eoinknd16.nexus.search

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Instant answers for the search bar: arithmetic ("12*(3+4)", "2^10",
 * "15% of 80", "sqrt 2") and unit conversion ("10 km in miles", "180 cm to ft",
 * "20c to f"). Returns null when the text isn't one of those.
 */
object QuickAnswers {

    data class Answer(val result: String, val expression: String)

    fun answer(raw: String): Answer? {
        val q = raw.trim().lowercase()
        if (q.isEmpty()) return null
        convert(q)?.let { return it }
        percentOf(q)?.let { return it }
        if (!q.any { it.isDigit() }) return null
        // Must look like maths, not just a number.
        if (q.none { it in "+-*/^×÷x%(" } && !q.startsWith("sqrt")) return null
        val v = runCatching { Calc(q.replace('×', '*').replace('÷', '/').replace("x", "*")).parse() }.getOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return Answer(format(v), raw.trim())
    }

    private fun percentOf(q: String): Answer? {
        val m = Regex("""^\s*([\d.]+)\s*%\s*of\s*([\d.]+)\s*$""").find(q) ?: return null
        val p = m.groupValues[1].toDoubleOrNull() ?: return null
        val n = m.groupValues[2].toDoubleOrNull() ?: return null
        return Answer(format(p / 100 * n), q)
    }

    fun format(v: Double): String =
        if (abs(v - v.roundToLong()) < 1e-9 && abs(v) < 1e15) "%,d".format(v.roundToLong())
        else "%,.6f".format(v).trimEnd('0').trimEnd('.')

    // ---- Units ----------------------------------------------------------------

    private data class Unit(val names: List<String>, val kind: String, val toBase: Double)

    private val units = listOf(
        // length (metres)
        Unit(listOf("mm", "millimetre", "millimetres", "millimeter", "millimeters"), "len", 0.001),
        Unit(listOf("cm", "centimetre", "centimetres", "centimeter", "centimeters"), "len", 0.01),
        Unit(listOf("m", "metre", "metres", "meter", "meters"), "len", 1.0),
        Unit(listOf("km", "kilometre", "kilometres", "kilometer", "kilometers", "k"), "len", 1000.0),
        Unit(listOf("in", "inch", "inches", "\""), "len", 0.0254),
        Unit(listOf("ft", "foot", "feet", "'"), "len", 0.3048),
        Unit(listOf("yd", "yard", "yards"), "len", 0.9144),
        Unit(listOf("mi", "mile", "miles"), "len", 1609.344),
        // mass (kg)
        Unit(listOf("g", "gram", "grams"), "mass", 0.001),
        Unit(listOf("kg", "kilo", "kilos", "kilogram", "kilograms"), "mass", 1.0),
        Unit(listOf("oz", "ounce", "ounces"), "mass", 0.0283495),
        Unit(listOf("lb", "lbs", "pound", "pounds"), "mass", 0.453592),
        Unit(listOf("st", "stone", "stones"), "mass", 6.35029),
        // volume (litres)
        Unit(listOf("ml", "millilitre", "millilitres", "milliliter", "milliliters"), "vol", 0.001),
        Unit(listOf("l", "litre", "litres", "liter", "liters"), "vol", 1.0),
        Unit(listOf("pint", "pints", "pt"), "vol", 0.568261),
        Unit(listOf("gal", "gallon", "gallons"), "vol", 4.54609),
        Unit(listOf("cup", "cups"), "vol", 0.25),
        Unit(listOf("floz", "fl oz"), "vol", 0.0284131),
        // speed (m/s)
        Unit(listOf("kmh", "km/h", "kph"), "speed", 1 / 3.6),
        Unit(listOf("mph"), "speed", 0.44704),
        Unit(listOf("m/s", "mps"), "speed", 1.0),
        // data (bytes)
        Unit(listOf("kb"), "data", 1e3), Unit(listOf("mb"), "data", 1e6), Unit(listOf("gb"), "data", 1e9), Unit(listOf("tb"), "data", 1e12),
        // time (seconds)
        Unit(listOf("s", "sec", "secs", "second", "seconds"), "time", 1.0),
        Unit(listOf("min", "mins", "minute", "minutes"), "time", 60.0),
        Unit(listOf("h", "hr", "hrs", "hour", "hours"), "time", 3600.0),
        Unit(listOf("day", "days"), "time", 86400.0),
        Unit(listOf("week", "weeks"), "time", 604800.0),
    )
    private val temps = mapOf(
        "c" to "c", "°c" to "c", "celsius" to "c",
        "f" to "f", "°f" to "f", "fahrenheit" to "f",
        "k" to "k", "kelvin" to "k",
    )

    private fun unitFor(name: String) = units.firstOrNull { name.trim() in it.names }

    private fun convert(q: String): Answer? {
        val m = Regex("""^\s*(-?[\d.,]+)\s*([a-z°/"' ]+?)\s+(?:in|to|as|=)\s+([a-z°/"' ]+)\s*$""").find(q) ?: return null
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val from = m.groupValues[2].trim()
        val to = m.groupValues[3].trim()
        val tf = temps[from]
        val tt = temps[to]
        if (tf != null && tt != null && !(from == "k" && unitFor(to) != null)) {
            val c = when (tf) { "c" -> n; "f" -> (n - 32) * 5 / 9; else -> n - 273.15 }
            val out = when (tt) { "c" -> c; "f" -> c * 9 / 5 + 32; else -> c + 273.15 }
            return Answer("${format(round(out))} °${tt.uppercase()}", "$n °${tf.uppercase()}")
        }
        val a = unitFor(from) ?: return null
        val b = unitFor(to) ?: return null
        if (a.kind != b.kind) return null
        val out = n * a.toBase / b.toBase
        return Answer("${format(round(out))} ${b.names.first()}", "${format(n)} ${a.names.first()}")
    }

    private fun round(v: Double): Double {
        val mag = if (v == 0.0) 0.0 else kotlin.math.floor(kotlin.math.log10(abs(v)))
        val decimals = (4 - mag).coerceIn(0.0, 8.0)
        val f = 10.0.pow(decimals)
        return (v * f).roundToLong() / f
    }

    // ---- Arithmetic (recursive descent) ----------------------------------------

    private class Calc(val s: String) {
        var i = 0
        fun parse(): Double {
            val v = expr()
            skip()
            require(i == s.length)
            return v
        }
        fun skip() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                when { eat('+') -> v += term(); eat('-') -> v -= term(); else -> return v }
            }
        }
        fun term(): Double {
            var v = power()
            while (true) {
                skip()
                when {
                    eat('*') -> v *= power()
                    eat('/') -> v /= power()
                    eat('%') -> v = if (peekDigit()) v % power() else v / 100
                    else -> return v
                }
            }
        }
        fun power(): Double {
            val b = unary()
            skip()
            return if (eat('^')) b.pow(power()) else b
        }
        fun unary(): Double {
            skip()
            if (eat('-')) return -unary()
            if (eat('+')) return unary()
            return atom()
        }
        fun atom(): Double {
            skip()
            if (s.startsWith("sqrt", i)) { i += 4; return sqrt(atom()) }
            if (s.startsWith("pi", i)) { i += 2; return Math.PI }
            if (eat('(')) { val v = expr(); skip(); require(eat(')')); return v }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            require(i > start)
            return s.substring(start, i).replace(",", "").toDouble()
        }
        fun eat(c: Char): Boolean { skip(); if (i < s.length && s[i] == c) { i++; return true }; return false }
        fun peekDigit(): Boolean { skip(); return i < s.length && (s[i].isDigit() || s[i] == '(') }
    }
}
