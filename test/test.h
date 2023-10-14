
typedef const std::string& Str;
typedef int32_t Int;
typedef time_t Ts;
using std::string;

struct FileInfo {
   std::string name;
   Ts modified;
};

struct MockFile {
   string name;
   string cont;
   Ts modified;
};


struct TestResults {
   Int countRun;
   Int countFailed;
};


template<typename T>
class Vec;

class IFileSys {
public:
   virtual bool dirExists(Str dir) = 0;
   virtual Vec<FileInfo> listFiles(Str dir) = 0;
   virtual Vec<string> listDirs(Str dir) = 0;
   virtual string readTextFile(Str dir, Str fN) = 0;
   virtual bool saveOverwriteFile(Str dir, Str fN, Str cont) = 0;
   virtual bool moveFile(Str dir, Str fN, Str targetDir) = 0;
   virtual string moveFileToNewVersion(Str dir, Str fN, Str targetDir) = 0;
   virtual bool deleteIfExists(Str dir, Str fN) = 0;
   virtual Vec<string> getNamesWithPrefix(Str dir, Str prefix) = 0;
};
