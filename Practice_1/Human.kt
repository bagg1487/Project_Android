package Practice_1

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

open class Human
{
    var name: String = ""
    var surname: String = ""
    var second_name: String = ""
    var group_number: Int = -1

    var x = 0
    var y = 0

    constructor(_name: String, _surname: String, _second: String, _gn: Int){
        name = _name
        surname = _surname
        second_name = _second
        group_number = _gn
        println("We created the Human object with name: $name")
    }

    open fun move(){
        Thread{
            val x = (1..10).random()
            val y = (1..10).random()
            println("Human is moved TO: $x,$y")
            println("Human is moved")
        }.start()

    }

    fun moveTo(_toX: Int, _toY: Int){
        x = _toX
        y = _toY
        println("Human is moved TO: $x,$y Default move")
    }
    fun randomMove(){
        val x = (1..100).random()
        val y = (1..100).random()
        println("Human is moved TO: $x,$y Random move")
    }
    fun GaussMarkovMove(){
        var speed = (1..20).random().toDouble()
        var direction = (0..360).random().toDouble()
        speed = 0.8 * speed + 0.2 * (1.0 + Random.Default.nextDouble() * 5.0)
        val directionRad = direction * PI / 180.0
        val newX = x + (speed * cos(directionRad)).toInt()
        val newY = y + (speed * sin(directionRad)).toInt()
        x = newX.coerceIn(0..1000)
        y = newY.coerceIn(0..1000)
        println("$name is moved TO: $x,$y Gauss-Markov")
    }
}