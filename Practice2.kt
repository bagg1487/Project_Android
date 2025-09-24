import kotlin.random.Random
import kotlin.math.*

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
        speed = 0.8 * speed + 0.2 * (1.0 + Random.nextDouble() * 5.0)
        val directionRad = direction * PI / 180.0
        val newX = x + (speed * cos(directionRad)).toInt()
        val newY = y + (speed * sin(directionRad)).toInt()
        x = newX.coerceIn(0..1000)
        y = newY.coerceIn(0..1000)
        println("$name is moved TO: $x,$y Gauss-Markov")
    }
}
class Driver(name: String, surname: String, second_name: String, val licenseCategory: String):
    Human(name, surname,  second_name, -1) {


    override fun move() {
        Thread {
        x += 15
        y += 10
        println("Driver $name drives straight to: $x,$y")
    }.start()
    }
}

fun main(){
    val humans = arrayOf(
        Human("Petya", "Ivanov", "Petrovich", 444),
        Human("Ivan", "Petrov", "Ivanovich", 444),
        Human("Maria", "Sidorova", "Alexandrovna", 444),
        Human("Alex", "Smirnov", "Sergeevich", 444),
        Human("Olga", "Kuznetsova", "Dmitrievna", 444),
        Human("Dmitry", "Kuplinov", "Vasilievich", 444),
        Human("Dauren", "Dauletovich", "Kystaubayev", 444),
        Human("Sergey", "Kozlov", "Olegovich", 444),
        Human("Elena", "Novikova", "Viktorovna", 444),
        Human("Andrey", "Morozov", "Nikolaevich", 444),
        Human("Tatiana", "Pavlova", "Pavlovna", 444),
        Human("Nikolay", "Orlov", "Anatolievich", 444),
        Human("Irina", "Volkova", "Fedorovna", 444),
        Human("Vladimir", "Soloviev", "Vladimirovich", 444),)
    
    val Maxim: Driver = Driver("Maxim", "Evgenievich", "Golopolosov","C")

    for (human in humans) {
           human.move()
    }
    Maxim.move()
    Thread.sleep(2000)
    }
