import os
import shutil

def clean_phone_storage(start_path, force_paths=None, force_names=None):
    if force_paths is None: force_paths = []
    if force_names is None: force_names = []
    
    # 将输入的强删路径统一转换为标准绝对路径，方便比对
    force_paths = [os.path.abspath(p) for p in force_paths]
    
    all_dirs = []
    force_deleted_count = 0
    
    print("🔍 开始扫描并处理目录...")
    
    # 使用 topdown=True 允许我们在遍历过程中动态修改 dirs 列表
    for root, dirs, files in os.walk(start_path, topdown=True):
        
        # 1. 安全第一：跳过 Android/data 和 Android/obb 核心系统目录
        if os.path.basename(root).lower() == 'android':
            if 'data' in dirs: dirs.remove('data')
            if 'obb' in dirs: dirs.remove('obb')
            continue # 跳过对 Android 目录自身的后续检查
            
        # 2. 检查子目录是否命中“强制删除名单”
        # 注意：遍历 list(dirs) 创建副本，防止边循环边删除导致遍历错乱
        for d in list(dirs):
            dir_path = os.path.abspath(os.path.join(root, d))
            
            # 判断是否命中精确路径，或者命中特定的文件夹名称
            if (dir_path in force_paths) or (d in force_names):
                try:
                    shutil.rmtree(dir_path) # 强制删除目录及其内部所有文件
                    dirs.remove(d)         # 从待扫描列表中移除，防止 os.walk 报错
                    print(f"💥 [强制删除] 成功清理顽固目录: {dir_path}")
                    force_deleted_count += 1
                except Exception as e:
                    print(f"⚠️ [强制删除失败] {dir_path}: {e}")
        
        # 3. 收集剩余可能为空的目录（排除根目录本身）
        if root != start_path:
            all_dirs.append(root)

    # 4. 剥洋葱式清理：按照路径从深到浅排序，处理普通的空目录
    all_dirs.sort(key=lambda p: p.count(os.sep), reverse=True)
    
    empty_deleted_count = 0
    print("\n🧹 开始检查并删除剩余的空目录...")
    
    for d in all_dirs:
        try:
            # 再次确认目录存在（没被前面的操作连带删掉）且确实为空
            if os.path.exists(d) and not os.listdir(d):
                os.rmdir(d)
                print(f"🗑️ [空目录删除] 成功: {d}")
                empty_deleted_count += 1
        except PermissionError:
            pass
        except Exception as e:
            print(f"⚠️ 无法处理 {d}: {e}")
            
    print("\n✨ 报告老板，清理完成！")
    print(f"📊 强制删除顽固目录：{force_deleted_count} 个")
    print(f"📊 自动清理空目录：{empty_deleted_count} 个")

if __name__ == "__main__":
    # 手机内部存储根目录
    sdcard = "/storage/emulated/0"
    
    # 📝 维度一：你要强制删除的【精确目录路径列表】（哪怕不为空也删）
    FORCE_DELETE_PATHS = [
        f"{sdcard}/A_Bad_App_Directory",  # 替换成你想强删的垃圾软件残留路径
        f"{sdcard}/Download/TempGarbage",
    ]
    
    # 📝 维度二：你要强制删除的【特定文件夹名称】（只要叫这个名，全手机通杀）
    # ⚠️ 警告：名字千万不要写成像 "cache" 这样太常见的词，容易误杀其他App的正常缓存
    FORCE_DELETE_NAMES = [
        ".thumbnails",   # 安卓极其流氓的相册缩略图缓存，经常占几个G
        "backups_old",   # 假设这是你自己不需要的旧备份文件夹名
    ]
    
    if os.path.exists(sdcard):
        clean_phone_storage(sdcard, force_paths=FORCE_DELETE_PATHS, force_names=FORCE_DELETE_NAMES)
    else:
        print("❌ 未找到存储路径，请检查 Termux 的存储权限。")
