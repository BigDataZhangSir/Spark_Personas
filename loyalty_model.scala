//��һ��
val result = hiveContext.sql("select max(login_times) from model_input_loyal_t")   //�����ʴ���
val max_login_times = result.collect()(0).get(0).asInstanceOf[Long].toDouble
val result = hiveContext.sql("select min(login_times) from model_input_loyal_t")   //��С���ʴ���
val min_login_times = result.collect()(0).get(0).asInstanceOf[Long].toDouble
val region_login_times = max_login_times - min_login_times

val result = hiveContext.sql("select max(stay_time) from model_input_loyal_t")  //���ͣ��ʱ��
val max_stay_time = result.collect()(0).get(0).asInstanceOf[Float].toDouble
val result = hiveContext.sql("select min(stay_time) from model_input_loyal_t")  //��Сͣ��ʱ��
val min_stay_time = result.collect()(0).get(0).asInstanceOf[Float].toDouble
val region_stay_time = max_stay_time - min_stay_time 

val result = hiveContext.sql("select max(view_days) from model_input_loyal_t")  //���ͣ������
val max_view_days = result.collect()(0).get(0).asInstanceOf[Long].toDouble
val result = hiveContext.sql("select min(view_days) from model_input_loyal_t")  //��Сͣ������
val min_view_days = result.collect()(0).get(0).asInstanceOf[Long].toDouble
val region_view_days = max_view_days - min_view_days 


val result = hiveContext.sql("select max(pv) from model_input_loyal_t")  //������ҳ����
val max_pv = result.collect()(0).get(0).asInstanceOf[Long].toDouble
val result = hiveContext.sql("select min(pv) from model_input_loyal_t")  //��С����ҳ����
val min_pv = result.collect()(0).get(0).asInstanceOf[Long].toDouble
val region_pv = max_pv - min_pv 

val result =hiveContext.sql("select max(unix_timestamp(t2.last_viewtime,'yyyy-MM-dd')) from  model_input_loyal_t t2")
val max_last_viewtime = result.collect()(0).get(0).asInstanceOf[Long].toDouble         //���ʱ��
val result = hiveContext.sql("select min(unix_timestamp(t2.last_viewtime,'yyyy-MM-dd')) from  model_input_loyal_t t2")
val min_last_viewtime = result.collect()(0).get(0).asInstanceOf[Long].toDouble     //��Сʱ��

val region_last_viewtime = max_last_viewtime - min_last_viewtime  


//Ȩ�أ�login_times:0.2,stay_time:0.3,view_days:0.3,pv:0.15,last_viewtime:0.05
val normalization= hiveContext.sql("select t1.cookie , (((t1.login_times - "+min_login_times+") * 0.2/"+region_login_times+") + ((t1.stay_time- "+min_stay_time+") * 0.3/"+region_stay_time+") +((t1.view_days - "+min_view_days+")* 0.3/"+region_view_days+") +((t1.pv - "+min_pv+")* 0.15/"+region_pv+") +((unix_timestamp(t1.last_viewtime,'yyyy-MM-dd')- "+min_last_viewtime+")*0.05 / " + region_last_viewtime + "))*100 as loyalty_score from model_input_loyal_t t1") 

normalization.registerTempTable("temporary_points")     //��һ����ʱ��

import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.mllib.linalg.Vectors

val rdd =  normalization.rdd.map( s => Vectors.dense(s.get(1).asInstanceOf[Double].toDouble))
val summary = Statistics.colStats(rdd)
println(summary.mean)
val means = summary.mean(0)
println(summary.variance)
val standard_deviation = summary.variance(0)

//����һ�������׼��ľ��룬��Ϊ��ֵ�Ƚ�С����ֵ��ȥ��׼���Ϊ�����������½�����Ϊ0���Ͻ粻�䣻
val r = means - standard_deviation*5
val low_bound =  if (r > 0)  r else 0
val up_bound = means + standard_deviation*5

val loyalty_temporary = hiveContext.sql("(select t1.lenovo_id,t1.loyalty_score,t1.loyalty_level from model_output_loyal_t t1 where 1=0) union all (select t2.cookie, t2.loyalty_score,(case when t2.loyalty_score  <= "+low_bound+"  then '��'  when t2.loyalty_score < "+up_bound+" then '��' else '��' end)as loyalty_level   from temporary_points t2)")

loyalty_temporary.registerTempTable("temporary_loyalty")

hiveContext.sql("insert overwrite table data.model_output_loyal_t  partition (l_day='2016-10-01') select * from temporary_loyalty")