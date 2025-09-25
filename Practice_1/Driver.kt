package Practice_1

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