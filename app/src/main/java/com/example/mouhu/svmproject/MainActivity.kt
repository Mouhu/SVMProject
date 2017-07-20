package com.example.mouhu.svmproject

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import com.example.mouhu.svmproject.util.Util
import java.io.*
import android.os.Bundle
import android.widget.Toast
import com.example.mouhu.svmproject.svm.svm_predict
import com.example.mouhu.svmproject.svm.svm_scale
import com.example.mouhu.svmproject.svm.svm_train
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    lateinit var mSensorManager: SensorManager
    lateinit var mAccSensor: Sensor
    var hz = (1000.0 * 1000.0 / 32).toInt()

    var lable = 0
    var trainNum = 0
    var currentCollectionTrainNum = 0

    var isStartCollection: Boolean = false

    var isStartTest: Boolean = false

    var mSensorEventLister = object : SensorEventListener {
        var accArr = DoubleArray(128)
        var currentIndex = 0

        override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun onSensorChanged(p0: SensorEvent) {
            //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            val x = p0.values[0]
            val y = p0.values[1]
            val z = p0.values[2]

            val acc = Math.sqrt((x * x + y * y + z * z).toDouble())
            tv_acc.text = "加速度：${acc}"

            if (currentIndex >= 128) {
                val features = Util.dataToFeatures(accArr, hz)

                if (isStartCollection) {
                    Util.writeToFile("${filesDir}/train", lable, features)
                    currentCollectionTrainNum++
                    tv_collection_num.text = "已经采集：${currentCollectionTrainNum}"

                    if (currentCollectionTrainNum >= trainNum){
                        collection()
                        currentCollectionTrainNum = 0
                    }
                }

                else {
                    val code = Util.predictUnScaleData(features)
                    result(code.toInt())
                }

                currentIndex = 0
            }

            else {
                accArr[currentIndex++] = acc
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rg_train_num.setOnCheckedChangeListener { radioGroup, i ->
            when (i) {
                R.id.rb_train_num_one -> trainNum = 30
                R.id.rb_train_num_two -> trainNum = 50
                R.id.rb_train_num_three -> trainNum = 100
            }

            tv_train_num.text = "采集样本数量:${trainNum}"
        }

        rg_train_num.check(R.id.rb_train_num_one)

        rg_lable.setOnCheckedChangeListener { radioGroup, i ->
            when (i) {
                R.id.rb_lable_one -> lable = 0
                R.id.rb_lable_two -> lable = 1
                R.id.rb_lable_three -> lable = 2
            }

            tv_lable.text = "标记动作:${lable}"
        }

        rg_lable.check(R.id.rb_lable_one)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 收集数据
        iv_collection.setOnClickListener {
            Toast.makeText(this@MainActivity, "点击了收集数据", Toast.LENGTH_SHORT).show()
            collection()
        }

        // 训练模型
        iv_train.setOnClickListener {
            Toast.makeText(this@MainActivity, "点击了训练", Toast.LENGTH_SHORT).show()
            iv_train.setImageResource(R.mipmap.sample)

            doAsync{
                createScaleFile(arrayOf("-l", "0", "-u", "1", "-s", "${filesDir}/range", "${filesDir}/train"))
                createModelFile(arrayOf("-s", "0", "-c", "128.0", "-t", "2", "-g", "8.0", "-e", "0.1", "${filesDir}/scale", "${filesDir}/model"))
                createPredictFile(arrayOf("${filesDir}/scale", "${filesDir}/model", "${filesDir}/predict"))

                runOnUiThread {
                    var reader = BufferedReader(InputStreamReader(FileInputStream("${filesDir}/accuracy")))
                    val line = reader.readLine()
                    tv_accuracy.text = line
                    iv_train.setImageResource(R.mipmap.sample_off)
                }
            }
        }

        // 测试
        iv_test.setOnClickListener {
            Toast.makeText(this@MainActivity, "点击了测试", Toast.LENGTH_SHORT).show()
            test()
        }

        btn_delete.setOnClickListener {
            doAsync{
                val file = File("${filesDir}")
                for (item in file.list()) {
                    File("${filesDir}/${item}").delete()
                }

                runOnUiThread {
                    toast("删除成功！")
                }
            }
        }
    }

    private fun createScaleFile(args: Array<String>) {
        val out = System.out
        var outScaleFile = PrintStream("${filesDir}/scale")
        System.setOut(outScaleFile)
        svm_scale.main(args)
        System.setOut(out)
    }

    private fun createModelFile(args: Array<String>) {
        svm_train.main(args)
    }

    private fun createPredictFile(args: Array<String>) {
        val out = System.out
        var outAccuracy = PrintStream("${filesDir}/accuracy")
        System.setOut(outAccuracy)
        svm_predict.main(args)
        System.setOut(out)
    }

    // 收集数据
    private fun collection() {
        // TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        if (!isStartCollection) {
            mSensorManager.registerListener(mSensorEventLister, mAccSensor, hz)
            iv_collection.setImageResource(R.mipmap.sample)
        } else {
            mSensorManager.unregisterListener(mSensorEventLister)
            iv_collection.setImageResource(R.mipmap.sample_off)
        }
        isStartCollection = !isStartCollection
    }

    private fun test() {
        if (!isStartTest) {
            Util.loadFile("${filesDir}/range", "${filesDir}/model")
            mSensorManager.registerListener(mSensorEventLister, mAccSensor, hz)
            iv_test.setImageResource(R.mipmap.test)
        }

        else {
            mSensorManager.unregisterListener(mSensorEventLister)
            iv_test.setImageResource(R.mipmap.test_off)
            result(-1)
        }

        isStartTest = !isStartTest
    }

    fun result(code: Int) {
        iv_still.setImageResource(R.mipmap.gait_still_off)
        iv_walk.setImageResource(R.mipmap.gait_walk_off)
        iv_run.setImageResource(R.mipmap.gait_run_off)

        when (code) {
            0 -> iv_still.setImageResource(R.mipmap.gait_still)
            1 -> iv_walk.setImageResource(R.mipmap.gait_walk)
            2 -> iv_run.setImageResource(R.mipmap.gait_run)
        }
    }
}
