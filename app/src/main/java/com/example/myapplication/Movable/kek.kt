package Practice_1

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

    val Maxim: Driver = Driver("Maxim", "Evgenievich", "Golopolosov", "C")

    for (human in humans) {
           human.move()
    }
    Maxim.move()
    Thread.sleep(2000)
    }