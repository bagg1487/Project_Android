import kotlin.random.Random
import kotlin.math.*

class Human
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

    fun move(){
        println("Human is moved")
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

    val petya: Human = Human("Petya","Ivanov","Petrovich",444)
    petya.move()
    petya.moveTo(10,100)
    petya.randomMove()
    petya.GaussMarkovMove()
    println("${petya.x}")

    var counter: Int = 10
    val name: String = ""
    println(name)
    println("Hello World!")

    val SimulatonTime = 10
    for(seconds in 1..SimulatonTime) {
        for (human in humans) {
            human.randomMove()
        }
        Thread.sleep(1000)
    }
    }
