package com.example.myapplication

import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets }

        val textView = findViewById<TextView>(R.id.textView)
        var currentText = ""
        val buttonsID = listOf(
            R.id.number0, R.id.number1, R.id.number2, R.id.number3, R.id.number4,
            R.id.number5, R.id.number6, R.id.number7, R.id.number8, R.id.number9
        )
        for(i in 0..buttonsID.size - 1){
            val buttonID = buttonsID[i]
            val button = findViewById<Button>(buttonID)
            button.setOnClickListener {
                currentText += button.text
                textView.text = currentText
            }
        }

        findViewById<Button>(R.id.division).setOnClickListener {
            currentText += "/"
            textView.text = currentText
        }
        findViewById<Button>(R.id.multipication).setOnClickListener {
            currentText += "*"
            textView.text = currentText
        }
        findViewById<Button>(R.id.minus).setOnClickListener {
            currentText += "-"
            textView.text = currentText
        }
        findViewById<Button>(R.id.plus).setOnClickListener {
            currentText += "+"
            textView.text = currentText
        }
        findViewById<Button>(R.id.clear).setOnClickListener {
            currentText = ""
            textView.text = currentText
        }
        findViewById<Button>(R.id.equals).setOnClickListener {
            val text = currentText.split("*", "/", "-", "+")
            val firstNumber = text[0].toDouble()
            val secondNumber = text[1].toDouble()
            for (symb in currentText){
                if(symb == '/'){
                    textView.text = (firstNumber / secondNumber).toString()
                }
                if(symb == '*'){
                    textView.text = (firstNumber * secondNumber).toString()
                }
                if(symb == '-'){
                    textView.text = (firstNumber - secondNumber).toString()
                }
                if(symb == '+'){
                    textView.text = (firstNumber + secondNumber).toString()
                }
            }
            }
        }

}

